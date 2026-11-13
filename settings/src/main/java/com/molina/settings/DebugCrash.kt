// molina-patch13d
package com.molina.settings

import com.molina.log.MolinaEvents
import com.molina.log.MolinaLog
import com.molina.log.MolinaLogger

/**
 * Alat uji alur CRASH/BUG. Hanya dipanggil dari tombol yang dibungkus BuildConfig.DEBUG di
 * SettingsFragment, jadi tidak terjangkau di build release. Berada di package com.molina.settings
 * agar CrashAttribution mengelompokkan crash uji ke area "settings".
 */
internal object DebugCrash {
    private val FIELDS = mapOf("source" to "settings")

    /** BUG terkontrol: tidak mematikan proses. Untuk menguji chip BUG dan bagian Bug di laporan. */
    fun bug(logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.bug("debug", "test-bug", "BUG uji terkontrol", fields = FIELDS, logger = logger)

    /**
     * Mencatat BEHAV pemicu (muncul di "Urutan perilaku sebelum crash"), lalu melempar exception
     * tak tertangani; CrashReporter mencatat CRASH dan proses mati seperti crash sungguhan.
     */
    fun crash(logger: MolinaLogger = MolinaLog.logger): Nothing {
        MolinaEvents.behavior("debug", "crash-trigger", "crash uji akan dipicu", fields = FIELDS, logger = logger)
        throw IllegalStateException("crash uji terkontrol", RuntimeException("penyebab uji"))
    }
}
