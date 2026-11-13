// molina-patch13
package com.molina.cinema

import com.molina.log.LogEntry
import com.molina.log.LogLevel
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
            PlayerEvent.Error(-1, "gagal membuka /sdcard/Movies/film.mkv"),
            PlayerEvent.Ended,
        )
        override fun load(uri: String) {}
        override fun play() {}
        override fun pause() {}
        override fun seekTo(positionMs: Long) {}
        override fun release() {}
    }

    @Test
    fun eventsBecomeStructuredEntriesWithoutPathsOrPosition() = runBlocking {
        val sink = CaptureSink()
        val session = LoggingMolinaPlayerSession(FakePlayer(), "p1", MolinaLogger(listOf(sink)))
        session.events.toList()
        val msgs = sink.entries.map { it.message }
        assertEquals(4, sink.entries.size)
        assertTrue(sink.entries.all { it.tag == "Molina/mpv/p1" })
        assertTrue(msgs[0].startsWith("[BEHAV:mpv.loaded]") && "file=film.mkv" in msgs[0])
        assertTrue(msgs[1].startsWith("[BEHAV:mpv.state]") && "state=PLAYING" in msgs[1])
        assertTrue(msgs[2].startsWith("[BUG:mpv.error] gagal membuka film.mkv") && "code=-1" in msgs[2])
        assertEquals(LogLevel.ERROR, sink.entries[2].level)
        assertTrue(msgs[3].startsWith("[BEHAV:mpv.ended]"))
        assertTrue(msgs.none { "rahasia" in it || "/sdcard" in it })
    }

    @Test
    fun commandsAreBehaviorAndSeekStaysDebug() {
        val sink = CaptureSink()
        val session = LoggingMolinaPlayerSession(FakePlayer(), "p1", MolinaLogger(listOf(sink)))
        session.load("file:///sdcard/film.mkv?token=rahasia")
        session.play()
        session.pause()
        session.seekTo(2500)
        session.release()
        val msgs = sink.entries.map { it.message }
        assertEquals(5, msgs.size)
        assertTrue(msgs[0].startsWith("[BEHAV:mpv.load]") && "file=film.mkv" in msgs[0])
        assertTrue(msgs[1].startsWith("[BEHAV:mpv.play]"))
        assertTrue(msgs[2].startsWith("[BEHAV:mpv.pause]"))
        assertEquals(LogLevel.DEBUG, sink.entries[3].level)
        assertTrue(msgs[4].startsWith("[BEHAV:mpv.release]"))
        assertTrue(msgs.none { "rahasia" in it })
    }
}
