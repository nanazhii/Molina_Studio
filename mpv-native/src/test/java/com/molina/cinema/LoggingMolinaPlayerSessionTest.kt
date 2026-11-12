package com.molina.cinema

import com.molina.log.LogEntry
import com.molina.log.LogSink
import com.molina.log.MolinaLogger
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggingMolinaPlayerSessionTest {
    private class CaptureSink : LogSink {
        val entries = CopyOnWriteArrayList<LogEntry>()
        override fun write(entry: LogEntry) { entries += entry }
    }

    private class FakePlayer : MolinaPlayerSession {
        override val events: Flow<PlayerEvent> = flowOf(
            PlayerEvent.Loaded("file:///sdcard/film.mkv?token=rahasia"),
            PlayerEvent.StateChanged(PlayerState.PLAYING),
            PlayerEvent.Position(1000, 5000),
            PlayerEvent.Error(-1, "gagal"),
            PlayerEvent.Ended,
        )
        override fun load(uri: String) {}
        override fun play() {}
        override fun pause() {}
        override fun seekTo(positionMs: Long) {}
        override fun release() {}
    }

    @Test
    fun logsEventsHidesQueryAndSkipsPosition() = runBlocking {
        val sink = CaptureSink()
        val session = LoggingMolinaPlayerSession(FakePlayer(), "p1", MolinaLogger(listOf(sink)))
        session.events.toList()
        assertTrue(sink.entries.all { it.tag == "Molina/mpv/p1" })
        assertTrue(sink.entries.any { it.message.contains("film.mkv") })
        assertTrue(sink.entries.none { it.message.contains("rahasia") })
        assertEquals(4, sink.entries.size)
    }
}
