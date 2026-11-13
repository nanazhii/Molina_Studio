// molina-patch11
package com.molina.terminal.proot

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.GZIPInputStream

/**
 * Pemasang proot statis dari GitHub Release (dipin di [ProotPin]): unduh -> verifikasi SHA-256 tgz
 * -> ekstrak ke staging -> verifikasi SHA-256 binary + ELF arm64 statis -> pindah ke filesDir/proot.
 * BLOCKING: panggil dari Dispatchers.IO. Aman diulang. Eksekusi harus dari filesDir (/sdcard noexec).
 */
class ProotInstaller(
    filesDir: File,
    private val isCancelled: () -> Boolean = { false },
) {
    private val prootDir = File(filesDir, "proot")
    private val bin = File(prootDir, "proot")
    private val marker = File(prootDir, "proot.ok")
    private val work = File(filesDir, "proot-dl")
    private val part = File(work, "proot.tgz.part")
    private val archive = File(work, "proot.tgz")
    private val staging = File(work, "stage")

    /** Terpasang oleh pemasang ini tetapi dari pin yang lebih lama: harus diganti. */
    fun isStale(): Boolean = marker.isFile && marker.readText().trim() != ProotPin.BIN_SHA256

    fun isInstalled(): Boolean =
        ProotPin.CONFIGURED && bin.isFile && bin.canExecute() &&
            marker.isFile && marker.readText().trim() == ProotPin.BIN_SHA256

    fun install(onVerifying: () -> Unit = {}) {
        if (!ProotPin.CONFIGURED) throw IOException("Pin proot belum diisi (jalankan workflow Native proot)")
        if (isInstalled()) return
        work.mkdirs()
        if (!(archive.isFile && Hashing.sha256Hex(archive) == ProotPin.TGZ_SHA256)) {
            archive.delete()
            download()
            onVerifying()
            if (Hashing.sha256Hex(archive) != ProotPin.TGZ_SHA256) {
                archive.delete()
                throw IOException("SHA-256 proot tidak cocok; berkas dibuang")
            }
        }
        onVerifying()
        deleteTree(staging)
        if (!staging.mkdirs()) throw IOException("Gagal membuat direktori staging proot")
        GZIPInputStream(BufferedInputStream(FileInputStream(archive), 64 * 1024), 64 * 1024).use { gz ->
            TarExtractor(staging, isCancelled).extract(gz)
        }
        val extracted = File(staging, "proot")
        if (!extracted.isFile) throw IOException("Arsip proot tidak berisi berkas proot")
        if (Hashing.sha256Hex(extracted) != ProotPin.BIN_SHA256) throw IOException("SHA-256 binary proot tidak cocok")
        val elf = ElfReader.parseFile(extracted)
        if (elf == null || !elf.isArm64) throw IOException("proot bukan ELF arm64")
        prootDir.mkdirs()
        marker.delete()
        bin.delete()
        Files.move(extracted.toPath(), bin.toPath(), StandardCopyOption.REPLACE_EXISTING)
        bin.setReadable(true, true)
        bin.setExecutable(true, true)
        marker.writeText(ProotPin.BIN_SHA256 + "\n")
        archive.delete()
        deleteTree(staging)
    }

    private fun download() {
        part.delete()
        val total = ProotPin.SIZE_BYTES
        val conn = URI(ProotPin.URL).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        try {
            val code = conn.responseCode
            if (code != 200) throw IOException("Unduhan proot gagal: HTTP $code")
            var done = 0L
            val buf = ByteArray(64 * 1024)
            FileOutputStream(part).use { out ->
                conn.inputStream.use { input ->
                    while (true) {
                        if (isCancelled()) throw InstallCancelledException()
                        val n = input.read(buf)
                        if (n < 0) break
                        done += n
                        if (done > total) throw IOException("Unduhan proot melebihi ukuran yang dipin")
                        out.write(buf, 0, n)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (part.length() != total) throw IOException("Unduhan proot terpotong (${part.length()}/$total)")
        if (!part.renameTo(archive)) throw IOException("Gagal memindahkan arsip proot")
    }

    private fun deleteTree(dir: File) {
        val p = dir.toPath()
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) return
        Files.walkFileTree(p, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }
}
