// molina-patch13
package com.molina.cinema

import com.molina.log.MolinaLog
import com.molina.log.MolinaLogger

/**
 * Menyambungkan MPVLib.LogObserver ke MpvLogBridge.
 * Pemakaian: attach() setelah MPVLib.create(), detach() sebelum MPVLib.destroy().
 * Aman bila .so belum ada (attach mengembalikan false dan tidak menyentuh MPVLib).
 */
object MpvNativeLog {
    private var bridge: MpvLogBridge? = null
    private var observer: MPVLib.LogObserver? = null

    @Synchronized
    fun attach(sessionId: String, logger: MolinaLogger = MolinaLog.logger): Boolean {
        detach()
        if (!MpvNative.isLoaded) return false
        val b = MpvLogBridge(sessionId, logger)
        val o = object : MPVLib.LogObserver {
            override fun logMessage(prefix: String, level: Int, text: String) = b.onMessage(prefix, level, text)
        }
        MPVLib.addLogObserver(o)
        bridge = b
        observer = o
        return true
    }

    @Synchronized
    fun detach() {
        val o = observer
        val b = bridge
        observer = null
        bridge = null
        if (o != null) runCatching { MPVLib.removeLogObserver(o) }
        b?.flushSuppressed()
    }
}
