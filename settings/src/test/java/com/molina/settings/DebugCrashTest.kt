// molina-patch13d
package com.molina.settings

import com.molina.log.CrashAttribution
import com.molina.log.LogEntry
import com.molina.log.LogLevel
import com.molina.log.LogSink
import com.molina.log.MolinaLogger
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Test

class DebugCrashTest {
    private class CaptureSink : LogSink {
        val entries = CopyOnWriteArrayList<LogEntry>()
        override fun write(entry: LogEntry) { entries += entry }
    }

    @Test
    fun crashLogsTriggerThenThrowsAttributedToSettings() {
        val sink = CaptureSink()
        val t = try {
            DebugCrash.crash(MolinaLogger(listOf(sink)))
        } catch (e: IllegalStateException) {
            e
        }
        assertEquals("settings", CrashAttribution.areaOf(t))
        assertEquals("penyebab uji", t.cause?.message)
        val e = sink.entries.single()
        assertEquals("Molina/debug", e.tag)
        assertEquals("[BEHAV:debug.crash-trigger] crash uji akan dipicu | source=settings", e.message)
    }

    @Test
    fun bugIsControlledErrorEvent() {
        val sink = CaptureSink()
        DebugCrash.bug(MolinaLogger(listOf(sink)))
        val e = sink.entries.single()
        assertEquals(LogLevel.ERROR, e.level)
        assertEquals("[BUG:debug.test-bug] BUG uji terkontrol | source=settings", e.message)
    }
}
