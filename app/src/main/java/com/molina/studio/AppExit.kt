// molina-patch17
package com.molina.studio

import android.os.Process
import android.os.SystemClock
import com.molina.log.ExitHooks
import com.molina.log.MolinaEvents
import com.molina.log.MolinaLog
import com.molina.terminal.core.SessionRegistry
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keluar total tanpa sisa: kait modul (mpv dilepas rapi), tutup sesi terminal, matikan semua
 * proses lain milik UID, verifikasi tidak ada yang tersisa, flush log, lalu matikan proses ini.
 */
internal object AppExit {
    private const val HOOK_TIMEOUT_MS = 1_500L
    private val started = AtomicBoolean(false)

    /** Panggil dari thread latar. Tidak kembali pada kondisi normal (proses dimatikan). */
    fun run(via: String) {
        if (!started.compareAndSet(false, true)) return
        val t0 = SystemClock.elapsedRealtime()
        try {
            MolinaEvents.behavior("app", "exit.requested", fields = mapOf("via" to via))
            val late = ExitHooks.runAll(HOOK_TIMEOUT_MS)
            if (late.isNotEmpty()) {
                MolinaEvents.bug("app", "exit-hook-timeout", "kait keluar melewati batas waktu", fields = mapOf("hooks" to late.joinToString(",")))
            }
            SessionRegistry.closeAll()
            val r = ProcessReaper.killOthers()
            val ms = SystemClock.elapsedRealtime() - t0
            MolinaEvents.behavior(
                "app", "exit.swept",
                fields = mapOf("killed" to r.killed.toString(), "leftover" to r.leftover.toString(), "ms" to ms.toString()),
            )
            if (r.leftover > 0) {
                MolinaEvents.bug("app", "exit-leftover", "ada proses yang tidak bisa dimatikan", fields = mapOf("leftover" to r.leftover.toString()))
            }
        } catch (t: Throwable) {
            MolinaEvents.bug("app", "exit-failed", t.message ?: t.javaClass.simpleName, t)
        } finally {
            runCatching { MolinaLog.logger.flush() }
            Process.killProcess(Process.myPid())
        }
    }
}
