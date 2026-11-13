// molina-patch21
package com.molina.terminal.proot

import com.molina.log.MolinaEvents
import java.io.File

/**
 * Memasang pembungkus nano dan molina-run ke rootfs Debian (nano diberi prioritas lewat /usr/local/bin).
 * Idempotent: menulis hanya bila isi berbeda; kegagalan dicatat sebagai BUG dan sesi lanjut.
 * Paket nano sendiri dipasang pengguna (apt-get install nano); pembungkus memberi pesan bila belum ada.
 */
object NanoSupport {
    class Asset(val resource: String, val target: String, val executable: Boolean)

    val ASSETS: List<Asset> = listOf(
        Asset("/molina/nano-wrapper.sh", "usr/local/bin/nano", true),
        Asset("/molina/molina-run.sh", "usr/local/bin/molina-run", true),
        Asset("/molina/nanorc", "etc/molina/nanorc", false),
    )

    fun resourceText(resource: String): String =
        NanoSupport::class.java.getResourceAsStream(resource)?.bufferedReader()?.use { it.readText() }
            ?: error("resource $resource tidak ditemukan")

    /** Jumlah berkas yang ditulis (0 bila semuanya sudah mutakhir). */
    fun install(rootfs: File, id: String? = null): Int {
        var written = 0
        try {
            for (a in ASSETS) {
                if (writeIfDifferent(File(rootfs, a.target), resourceText(a.resource), a.executable)) written++
            }
            if (written > 0) {
                MolinaEvents.behavior("proot", "nano-install", fields = mapOf("written" to written.toString()), id = id)
            }
        } catch (e: Exception) {
            MolinaEvents.bug("proot", "nano-install-failed", e.message ?: e.javaClass.simpleName, e, id = id)
        }
        return written
    }

    private fun writeIfDifferent(f: File, text: String, executable: Boolean): Boolean {
        val same = f.isFile && f.readText() == text
        if (!same) {
            f.parentFile?.mkdirs()
            f.writeText(text)
        }
        f.setReadable(true, false)
        if (executable && !f.canExecute()) f.setExecutable(true, false)
        return !same
    }
}
