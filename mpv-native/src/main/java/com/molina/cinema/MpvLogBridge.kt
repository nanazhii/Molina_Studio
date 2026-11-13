// molina-patch13
package com.molina.cinema

import com.molina.log.LogLevel
import com.molina.log.MolinaEvents
import com.molina.log.MolinaLog
import com.molina.log.MolinaLogger

/**
 * Meneruskan log internal libmpv level FATAL/ERROR ke molina.log sebagai [BUG:mpv.native].
 * Tidak mereferensikan MPVLib, sehingga aman diuji di JVM. Dibatasi (rate-limit) agar libmpv yang
 * berisik tidak membanjiri log; sisa yang dibuang dilaporkan sebagai [BUG:mpv.native-suppressed].
 */
class MpvLogBridge(
    private val sessionId: String,
    private val logger: MolinaLogger = MolinaLog.logger,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxPerWindow: Int = 5,
    private val windowMs: Long = 10_000L,
) {
    private var windowStart = 0L
    private var started = false
    private var emitted = 0
    private var suppressed = 0

    /** Dipanggil dari thread event mpv. level mengikuti MpvLogLevel (FATAL=10, ERROR=20). */
    fun onMessage(prefix: String, level: Int, text: String) {
        if (level <= 0 || level > LEVEL_ERROR) return
        val msg = MpvLogScrub.scrub(text.trim())
        if (msg.isEmpty()) return

        var report = 0
        var allowed = false
        synchronized(this) {
            val now = clock()
            if (!started || now - windowStart >= windowMs) {
                report = suppressed
                suppressed = 0
                emitted = 0
                windowStart = now
                started = true
            }
            if (emitted < maxPerWindow) { emitted++; allowed = true } else suppressed++
        }
        if (report > 0) reportSuppressed(report)
        if (allowed) {
            MolinaEvents.bug(
                "mpv", "native", msg,
                fields = mapOf("prefix" to prefix, "level" to if (level <= LEVEL_FATAL) "fatal" else "error"),
                id = sessionId, logger = logger,
            )
        }
    }

    /** Laporkan sisa pesan yang dibuang; dipanggil saat sesi dilepas. */
    fun flushSuppressed() {
        var report = 0
        synchronized(this) { report = suppressed; suppressed = 0 }
        if (report > 0) reportSuppressed(report)
    }

    private fun reportSuppressed(count: Int) {
        MolinaEvents.bug(
            "mpv", "native-suppressed", "log libmpv dibatasi",
            fields = mapOf("count" to count.toString()),
            id = sessionId, level = LogLevel.WARN, logger = logger,
        )
    }

    private companion object {
        const val LEVEL_FATAL = 10
        const val LEVEL_ERROR = 20
    }
}
