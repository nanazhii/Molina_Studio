// molina-ytdlp-install
package com.molina.core.common.stream

/** Tahap pemasangan mesin ekstraksi (yt-dlp), berurutan. */
enum class YtDlpInstallStage { PACKAGES, DOWNLOAD, VERIFY, TEST }

/** Hasil pemasangan. [Failed.code] selalu anggota [YtDlpInstallProtocol.FAIL_CODES]: aman dicatat dan ditampilkan. */
sealed class YtDlpInstallResult {
    data class Done(val version: String) : YtDlpInstallResult()
    data class Failed(val code: String) : YtDlpInstallResult()
}

/**
 * Kontrak pemasang yt-dlp. Fitur (pemutar) hanya mengenal kontrak ini; implementasi (Debian PRoot) dipasang :app
 * lewat [YtDlpInstallerRegistry], pola sama dengan LocalPickerRegistry.
 */
interface YtDlpInstaller {
    /** Menyentuh berkas rootfs: jangan dipanggil di main thread. */
    fun isInstalled(): Boolean

    /**
     * Pasang yt-dlp. Suspend dan bisa dibatalkan (proses dimatikan). [onStage] dipanggil dari thread latar,
     * bukan main thread. Satu pemasangan pada satu waktu; yang kedua langsung gagal dengan kode BUSY.
     */
    suspend fun install(onStage: (YtDlpInstallStage) -> Unit): YtDlpInstallResult
}

object YtDlpInstallerRegistry {
    @Volatile private var installer: YtDlpInstaller? = null

    fun install(installer: YtDlpInstaller?) {
        this.installer = installer
    }

    /** Pemasang terpasang, atau null bila belum ada (pemanggil wajib menangani). */
    fun get(): YtDlpInstaller? = installer
}

/**
 * Protokol baris antara skrip pasang_ytdlp.sh (stdout) dan aplikasi. Hanya tiga bentuk baris yang dikenal;
 * selebihnya diabaikan. Versi disaring ketat dan kode gagal dibatasi himpunan tetap, sehingga keluaran skrip
 * tidak pernah menyelundupkan teks bebas ke log atau layar.
 */
object YtDlpInstallProtocol {
    const val STEP = "MOLINA_STEP "
    const val DONE = "MOLINA_DONE "
    const val FAIL = "MOLINA_FAIL "

    val FAIL_CODES: Set<String> =
        setOf("NO_ROOTFS", "NO_NETWORK", "NO_PACKAGES", "BAD_HASH", "FAILED", "TIMEOUT", "BUSY")

    sealed class Event {
        data class Stage(val stage: YtDlpInstallStage) : Event()
        data class Done(val version: String) : Event()
        data class Fail(val code: String) : Event()
    }

    private val VERSION = Regex("[0-9A-Za-z._-]{1,32}")

    fun parse(line: String): Event? {
        val s = line.trim()
        return when {
            s.startsWith(STEP) -> {
                val name = s.removePrefix(STEP).trim()
                YtDlpInstallStage.values().firstOrNull { it.name == name }?.let { Event.Stage(it) }
            }
            s.startsWith(DONE) -> Event.Done(s.removePrefix(DONE).trim().takeIf { VERSION.matches(it) } ?: "")
            s.startsWith(FAIL) -> {
                val code = s.removePrefix(FAIL).trim()
                Event.Fail(if (code in FAIL_CODES) code else "FAILED")
            }
            else -> null
        }
    }
}
