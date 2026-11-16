package com.molina.terminal.proot

import com.molina.core.security.MolinaShield
import com.molina.log.MolinaEvents
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Opt-in: arahkan lalu lintas http/https alat di Debian (apt, pip, curl, wget, yt-dlp, git via https)
 * lewat Molina Shield. Guest dijalankan dengan `env -i`, jadi lingkungan induk tidak diwariskan;
 * variabel proxy ditulis ke `etc/profile.d/molina-shield.sh` yang dibaca `bash --login`. Berkas ini
 * tidak ada di argumen proses (tidak tampil di `ps`), dibuat ulang tiap sesi, dan hanya dapat dibaca
 * pemilik. Token hanya berlaku sampai Shield dimulai ulang; sesi yang sudah berjalan memakai token lama.
 *
 * - OFF: berkas dihapus (opt-in mati, Shield mati, atau Shield tidak ada).
 * - PROXY: variabel mengarah ke proxy lokal.
 * - FAIL_CLOSED: opt-in dan Shield menyala tetapi proxy mati; variabel diarahkan ke port mati
 *   sehingga alat gagal tersambung, bukan keluar tanpa penyaringan.
 * Tidak mencakup ssh/git+ssh, protokol non-HTTP, dan program yang mengabaikan variabel proxy.
 */
object ShieldGuestEnv {
    const val SCRIPT_PATH = "etc/profile.d/molina-shield.sh"
    private const val NO_PROXY = "localhost,127.0.0.1,::1"
    private val DEAD_URL = "http" + "://127.0.0.1:9"
    private val SAFE_URL = Regex("^[A-Za-z0-9:/@._-]+$")

    enum class Mode { OFF, PROXY, FAIL_CLOSED }

    class Plan(val mode: Mode, val url: String?)

    fun plan(shield: MolinaShield?, optIn: Boolean): Plan {
        if (!optIn || shield == null || !shield.config().enabled) return Plan(Mode.OFF, null)
        val ep = shield.endpoint() // dibaca sekali agar tidak berubah di tengah keputusan
        return if (ep != null) Plan(Mode.PROXY, ep.url()) else Plan(Mode.FAIL_CLOSED, DEAD_URL)
    }

    fun scriptText(url: String): String {
        require(SAFE_URL.matches(url)) { "URL proxy memuat karakter tak aman" }
        return "# Molina Shield: dibuat ulang tiap sesi terminal. Jangan disunting; token berubah tiap Shield dimulai.\n" +
            "export http_proxy='$url' https_proxy='$url' HTTP_PROXY='$url' HTTPS_PROXY='$url'\n" +
            "export no_proxy='$NO_PROXY' NO_PROXY='$NO_PROXY'\n"
    }

    /** Tidak pernah melempar. Hanya mode yang dicatat (BEHAV term.shield-env), tidak pernah URL atau token. */
    fun install(rootfs: File, shield: MolinaShield?, optIn: Boolean, id: String? = null): Mode {
        val f = File(rootfs, SCRIPT_PATH)
        return try {
            val p = plan(shield, optIn)
            if (p.url == null) Files.deleteIfExists(f.toPath()) else write(f, scriptText(p.url))
            MolinaEvents.behavior("term", "shield-env", fields = mapOf("mode" to p.mode.name.lowercase()), id = id)
            p.mode
        } catch (e: Exception) {
            runCatching { Files.deleteIfExists(f.toPath()) }
            MolinaEvents.bug("proot", "shield-env-failed", e.javaClass.simpleName, e, id = id)
            Mode.OFF
        }
    }

    private fun write(f: File, text: String) {
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        tmp.setReadable(false, false)
        tmp.setReadable(true, true)
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
