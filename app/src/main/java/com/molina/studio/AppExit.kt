// molina-patch16
package com.molina.studio

import android.os.Process
import com.molina.log.MolinaEvents
import com.molina.log.MolinaLog
import com.molina.terminal.core.SessionRegistry
import java.util.concurrent.atomic.AtomicBoolean

/** Keluar total: tutup sesi terminal, matikan sisa proses, flush log, lalu matikan proses aplikasi. */
internal object AppExit {
    private val started = AtomicBoolean(false)

    /** Panggil dari thread latar. Tidak kembali pada kondisi normal (proses dimatikan). */
    fun run(via: String) {
        if (!started.compareAndSet(false, true)) return
        try {
            MolinaEvents.behavior("app", "exit.requested", fields = mapOf("via" to via))
            SessionRegistry.closeAll()
            val killed = ProcessReaper.killOthers()
            MolinaEvents.behavior("app", "exit.swept", fields = mapOf("killed" to killed.toString()))
        } catch (t: Throwable) {
            MolinaEvents.bug("app", "exit-failed", t.message ?: t.javaClass.simpleName, t)
        } finally {
            runCatching { MolinaLog.logger.flush() }
            Process.killProcess(Process.myPid())
        }
    }
}
