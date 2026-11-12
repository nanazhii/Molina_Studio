package com.molina.terminal.proot

import android.system.Os
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption

class TarFormatException(message: String) : IOException(message)

class InstallCancelledException : IOException("Dibatalkan")

internal object TarPaths {
    /**
     * Nama entri aman: relatif, tanpa "..", tanpa NUL.
     * Mengembalikan jalur bersih, "" untuk akar ("." atau "./"), atau null bila berbahaya.
     */
    fun clean(raw: String): String? {
        if (raw.contains('\u0000') || raw.startsWith("/")) return null
        val parts = raw.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." }) return null
        return parts.joinToString("/")
    }
}

/**
 * Pengekstrak tar minimal (ustar) untuk rootfs Debian. Tanpa dependensi.
 * Mendukung berkas, direktori, symlink, hardlink (disalin). Gagal-tertutup untuk
 * format yang tidak dikenali (pax/GNU long name) agar tidak salah menulis jalur.
 * Tidak pernah menulis melewati symlink; mode minimal pemilik rw (berkas) / rwx (direktori).
 */
class TarExtractor(
    private val root: File,
    private val isCancelled: () -> Boolean = { false },
) {
    private val checkedDirs = HashSet<String>()
    private val buf = ByteArray(64 * 1024)

    var entries = 0
        private set

    fun extract(input: InputStream, onEntry: (Int) -> Unit = {}) {
        val header = ByteArray(BLOCK)
        var zeroBlocks = 0
        while (true) {
            if (isCancelled()) throw InstallCancelledException()
            if (!readFully(input, header)) break
            if (header.all { it == 0.toByte() }) {
                zeroBlocks++
                if (zeroBlocks >= 2) break
                continue
            }
            zeroBlocks = 0
            verifyChecksum(header)

            val type = (header[156].toInt() and 0xff).toChar()
            val size = parseOctal(header, 124, 12)
            val mode = parseOctal(header, 100, 8).toInt()
            var name = field(header, 0, 100)
            if (field(header, 257, 6).startsWith("ustar")) {
                val prefix = field(header, 345, 155)
                if (prefix.isNotEmpty()) name = "$prefix/$name"
            }
            val link = field(header, 157, 100)
            val rel = TarPaths.clean(name) ?: throw TarFormatException("nama entri berbahaya: $name")

            when (type) {
                '0', '\u0000' -> {
                    if (rel.isEmpty()) throw TarFormatException("berkas tanpa nama")
                    writeFile(rel, mode, size, input)
                }
                '5' -> if (rel.isNotEmpty()) makeDir(rel, mode)
                '2' -> makeSymlink(rel, link)
                '1' -> makeHardlink(rel, link, mode)
                'x', 'g', 'L', 'K' -> throw TarFormatException("tipe header '$type' tidak didukung")
                else -> skipExact(input, size) // perangkat/FIFO: dilewati
            }
            skipExact(input, (BLOCK - size % BLOCK) % BLOCK)
            entries++
            onEntry(entries)
        }
    }

    private fun writeFile(rel: String, mode: Int, size: Long, input: InputStream) {
        val out = target(rel)
        Files.deleteIfExists(out.toPath())
        out.outputStream().buffered(buf.size).use { copyExact(input, it, size) }
        Os.chmod(out.path, (mode and MODE_MASK) or OWNER_RW)
    }

    private fun makeDir(rel: String, mode: Int) {
        val dir = target(rel)
        val p = dir.toPath()
        if (Files.isSymbolicLink(p)) throw TarFormatException("direktori menimpa symlink: $rel")
        if (!dir.exists() && !dir.mkdir()) throw IOException("gagal membuat direktori $rel")
        if (!dir.isDirectory) throw TarFormatException("bukan direktori: $rel")
        checkedDirs.add(dir.path)
        Os.chmod(dir.path, (mode and MODE_MASK) or OWNER_RWX)
    }

    private fun makeSymlink(rel: String, linkTarget: String) {
        if (rel.isEmpty() || linkTarget.isEmpty()) throw TarFormatException("symlink tidak valid: $rel")
        val out = target(rel)
        Files.deleteIfExists(out.toPath())
        Os.symlink(linkTarget, out.path)
    }

    private fun makeHardlink(rel: String, linkName: String, mode: Int) {
        val srcRel = TarPaths.clean(linkName)?.takeIf { it.isNotEmpty() }
            ?: throw TarFormatException("hardlink tidak valid: $linkName")
        val src = target(srcRel)
        if (!Files.isRegularFile(src.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw TarFormatException("sumber hardlink bukan berkas biasa: $srcRel")
        }
        val out = target(rel)
        Files.deleteIfExists(out.toPath())
        Files.copy(src.toPath(), out.toPath())
        Os.chmod(out.path, (mode and MODE_MASK) or OWNER_RW)
    }

    /** File di bawah root; semua leluhur dipastikan direktori sungguhan (bukan symlink). */
    private fun target(rel: String): File {
        val f = File(root, rel)
        val chain = ArrayList<File>()
        var d: File? = f.parentFile
        while (d != null && d != root) {
            chain.add(d)
            d = d.parentFile
        }
        if (d == null) throw TarFormatException("jalur di luar akar: $rel")
        for (c in chain.asReversed()) {
            if (!checkedDirs.add(c.path)) continue
            val p = c.toPath()
            if (Files.isSymbolicLink(p)) {
                checkedDirs.remove(c.path)
                throw TarFormatException("jalur melewati symlink: ${c.path}")
            }
            if (!c.exists()) {
                if (!c.mkdir()) throw IOException("gagal membuat ${c.path}")
            } else if (!c.isDirectory) {
                throw TarFormatException("bukan direktori: ${c.path}")
            }
        }
        return f
    }

    private fun verifyChecksum(h: ByteArray) {
        var sum = 0L
        for (i in 0 until BLOCK) sum += if (i in 148..155) 32 else (h[i].toInt() and 0xff)
        if (sum != parseOctal(h, 148, 8)) throw TarFormatException("checksum header tidak cocok")
    }

    private fun parseOctal(b: ByteArray, off: Int, len: Int): Long {
        if ((b[off].toInt() and 0x80) != 0) throw TarFormatException("angka base-256 tidak didukung")
        var i = off
        val end = off + len
        while (i < end && b[i] == ' '.code.toByte()) i++
        var v = 0L
        while (i < end) {
            val c = b[i].toInt()
            if (c < '0'.code || c > '7'.code) break
            v = v * 8 + (c - '0'.code)
            i++
        }
        return v
    }

    private fun field(b: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && b[end] != 0.toByte()) end++
        return String(b, off, end - off, Charsets.UTF_8)
    }

    private fun readFully(input: InputStream, dst: ByteArray): Boolean {
        var off = 0
        while (off < dst.size) {
            val n = input.read(dst, off, dst.size - off)
            if (n < 0) {
                if (off == 0) return false
                throw EOFException("tar terpotong")
            }
            off += n
        }
        return true
    }

    private fun copyExact(input: InputStream, out: OutputStream, size: Long) {
        var left = size
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) throw EOFException("tar terpotong")
            out.write(buf, 0, n)
            left -= n
        }
    }

    private fun skipExact(input: InputStream, count: Long) {
        var left = count
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) throw EOFException("tar terpotong")
            left -= n
        }
    }

    private companion object {
        const val BLOCK = 512
        const val MODE_MASK = 4095 // 07777
        const val OWNER_RW = 384 // 0600
        const val OWNER_RWX = 448 // 0700
    }
}
