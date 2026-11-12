package com.molina.terminal.core

import com.molina.log.LogEntry
import com.molina.log.LogSink
import com.molina.log.MolinaLogger
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggingTerminalSessionTest {
    private class CaptureSink : LogSink {
        val entries = CopyOnWriteArrayList<LogEntry>()
        override fun write(entry: LogEntry) { entries += entry }
    }

    @Test
    fun logsLifecycleWithoutLeakingContent() = runBlocking {
        val sink = CaptureSink()
        val inner = ProcessTerminalSession(SessionConfig(listOf("sh", "-c", "echo rahasia")))
        val session = LoggingTerminalSession(inner, "t1", MolinaLogger(listOf(sink)))
        session.events.toList()
        assertTrue(sink.entries.all { it.tag == "Molina/term/t1" })
        assertTrue(sink.entries.any { it.message.contains("sesi selesai") })
        assertTrue(sink.entries.none { it.message.contains("rahasia") })
    }
}
