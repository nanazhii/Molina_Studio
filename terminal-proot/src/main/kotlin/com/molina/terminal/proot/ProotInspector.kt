package com.molina.terminal.proot

import java.io.File
import java.io.FileInputStream

object ProotLocation {
    /** Folder yang diisi pengguna secara manual: proot + pustaka (+ loader bila ada). */
    const val EXTERNAL_DIR = "/storage/emulated/0/Molina/proot"
}

class ElfInfo(
    val is64: Boolean,
    val littleEndian: Boolean,
    val machine: Int,
    val interpreter: String?,
) {
    val isArm64: Boolean get() = is64 && littleEndian && machine == EM_AARCH64
    val isStatic: Boolean get() = interpreter == null

    companion object {
        const val EM_AARCH64 = 183
    }
}

object ElfReader {
    fun parseFile(file: File): ElfInfo? {
        val head = ByteArray(4096)
        val n = FileInputStream(file).use { it.read(head) }
        return if (n <= 0) null else parse(head.copyOf(n))
    }

    fun parse(b: ByteArray): ElfInfo? {
        if (b.size < 64) return null
        if (b[0] != 0x7f.toByte() || b[1] != 'E'.code.toByte() ||
            b[2] != 'L'.code.toByte() || b[3] != 'F'.code.toByte()
        ) return null
        val is64 = b[4].toInt() == 2
        val le = b[5].toInt() == 1
        if (!is64 || !le) return ElfInfo(is64, le, 0, null)
        val machine = u16(b, 18)
        val phoff = u64(b, 32)
        val phentsize = u16(b, 54)
        val phnum = u16(b, 56)
        var interp: String? = null
        for (i in 0 until phnum) {
            val o = phoff + i.toLong() * phentsize
            if (o < 0 || o + 56 > b.size) break
            val po = o.toInt()
            if (u32(b, po) == PT_INTERP) {
                val off = u64(b, po + 8)
                val sz = u64(b, po + 32)
                if (sz > 1 && off >= 0 && off + sz <= b.size) {
                    interp = String(b, off.toInt(), (sz - 1).toInt(), Charsets.UTF_8)
                }
            }
        }
        return ElfInfo(is64, le, machine, interp)
    }

    private const val PT_INTERP = 3L

    private fun u16(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8)

    private fun u32(b: ByteArray, o: Int): Long {
        var v = 0L
        for (i in 3 downTo 0) v = (v shl 8) or (b[o + i].toLong() and 0xff)
        return v
    }

    private fun u64(b: ByteArray, o: Int): Long {
        var v = 0L
        for (i in 7 downTo 0) v = (v shl 8) or (b[o + i].toLong() and 0xff)
        return v
    }
}

class ProotReport(
    val dir: File,
    val elf: ElfInfo?,
    val loaderFound: Boolean,
    val libs: List<String>,
    val problems: List<String>,
) {
    val ok: Boolean get() = problems.isEmpty()
}

/** Diagnostik folder proot buatan pengguna. Hanya membaca, tidak mengubah apa pun. */
object ProotInspector {
    fun inspect(dir: File = File(ProotLocation.EXTERNAL_DIR)): ProotReport {
        val problems = ArrayList<String>()
        var elf: ElfInfo? = null
        val bin = File(dir, "proot")
        if (!dir.isDirectory) {
            problems += "Folder tidak ditemukan: ${dir.path}"
        } else if (!bin.isFile) {
            problems += "Berkas proot tidak ada di ${dir.path}"
        } else {
            elf = ElfReader.parseFile(bin)
            when {
                elf == null -> problems += "proot bukan berkas ELF"
                !elf.isArm64 -> problems += "proot bukan arm64 (machine=${elf.machine})"
            }
        }
        val names = dir.list()?.sorted().orEmpty()
        val libs = names.filter { it.contains(".so") }
        val loader = names.contains("loader")
        if (elf != null && !elf.isStatic && libs.isEmpty()) {
            problems += "proot dinamis (interp=${elf.interpreter}) tetapi tidak ada pustaka .so di folder"
        }
        return ProotReport(dir, elf, loader, libs, problems)
    }
}
