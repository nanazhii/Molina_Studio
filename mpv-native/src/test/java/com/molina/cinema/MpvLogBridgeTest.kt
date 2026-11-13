// molina-patch13
package com.molina.cinema

import com.molina.log.LogEntry
import com.molina.log.LogLevel
import com.molina.log.LogSink
import com.molina.log.MolinaLogger
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvLogBridgeTest {
    private class CaptureSink : LogSink {
        val entries = CopyOnWriteArrayList<LogEntry>()
        override fun write(entry: LogEntry) { entries += entry }
    }

    private var now = 0L
    private val sink = CaptureSink()

    private fun bridge(max: Int = 5) =
        MpvLogBridge("p1", MolinaLogger(listOf(sink)), clock = { now }, maxPerWindow = max, windowMs = 10_000L)

    @Test
    fun onlyFatalAndErrorBecomeBugEvents() {
        val b = bridge()
        b.onMessage("demux", 30, "peringatan biasa")
        b.onMessage("cplayer", 0, "none")
        b.onMessage("ffmpeg", 20, "Invalid data found when processing input\n")
        b.onMessage("vd", 10, "decoder crash di /sdcard/Movies/film.mkv")
        assertEquals(2, sink.entries.size)
        val e = sink.entries[0]
        assertEquals("Molina/mpv/p1", e.tag)
        assertEquals(LogLevel.ERROR, e.level)
        assertEquals("[BUG:mpv.native] Invalid data found when processing input | prefix=ffmpeg level=error", e.message)
        assertTrue("level=fatal" in sink.entries[1].message)
        assertTrue(sink.entries.none { "/sdcard" in it.message })
    }

    @Test
    fun rateLimitReportsSuppressedCountWhenWindowRolls() {
        val b = bridge(max = 2)
        repeat(5) { b.onMessage("ffmpeg", 20, "err $it") }
        assertEquals(2, sink.entries.size)
        now = 10_000L
        b.onMessage("ffmpeg", 20, "err baru")
        assertEquals(4, sink.entries.size)
        assertTrue(sink.entries[2].message.startsWith("[BUG:mpv.native-suppressed]") && "count=3" in sink.entries[2].message)
        assertEquals(LogLevel.WARN, sink.entries[2].level)
        assertTrue(sink.entries[3].message.startsWith("[BUG:mpv.native] err baru"))
    }

    @Test
    fun flushReportsLeftoverOnce() {
        val b = bridge(max = 1)
        repeat(3) { b.onMessage("ffmpeg", 20, "err") }
        b.flushSuppressed()
        b.flushSuppressed()
        assertEquals(2, sink.entries.size)
        assertTrue("count=2" in sink.entries[1].message)
    }
}
