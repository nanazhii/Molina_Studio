// molina-patch13b
package com.molina.terminal.core

import com.molina.log.LogEntry
import com.molina.log.LogLevel
import com.molina.log.LogSink
import com.molina.log.MolinaLogger
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggingTerminalSessionTest {
    private class CaptureSink : LogSink {
        val entries = CopyOnWriteArrayList<LogEntry>()
        override fun write(entry: LogEntry) { entries += entry }
    }

    @Test
    fun cleanExitIsBehaviorWithoutLeakingContent() = runBlocking {
        val sink = CaptureSink()
        val inner = ProcessTerminalSession(SessionConfig(listOf("sh", "-c", "echo rahasia")))
        val session = LoggingTerminalSession(inner, "t1", MolinaLogger(listOf(sink)))
        session.events.toList()
        assertTrue(sink.entries.all { it.tag == "Molina/term/t1" })
        assertTrue(sink.entries.any { it.message.startsWith("[BEHAV:term.exit] | code=0") })
        assertTrue(sink.entries.none { it.message.contains("rahasia") })
    }

    @Test
    fun earlyNonZeroExitIsBug() = runBlocking {
        val sink = CaptureSink()
        val inner = ProcessTerminalSession(SessionConfig(listOf("sh", "-c", "exit 3")))
        LoggingTerminalSession(inner, "t2", MolinaLogger(listOf(sink))).events.toList()
        val bug = sink.entries.first { it.message.startsWith("[BUG:term.exit-early]") }
        assertEquals(LogLevel.ERROR, bug.level)
        assertTrue("code=3" in bug.message)
    }

    @Test
    fun spawnFailureIsBug() = runBlocking {
        val sink = CaptureSink()
        val inner = ProcessTerminalSession(SessionConfig(listOf("/tidak/ada/shell")))
        LoggingTerminalSession(inner, "t3", MolinaLogger(listOf(sink))).events.toList()
        val bug = sink.entries.first { it.message.startsWith("[BUG:term.spawn-failed]") }
        assertEquals(LogLevel.ERROR, bug.level)
        assertTrue("error=IOException" in bug.message)
    }
}
