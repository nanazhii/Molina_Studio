// molina-patch13e
package com.molina.cinema

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvEventMapperTest {
    private val start = MPVLib.MpvEvent.MPV_EVENT_START_FILE
    private val loadedEv = MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED
    private val end = MPVLib.MpvEvent.MPV_EVENT_END_FILE

    @Test
    fun successfulLoadEmitsLoadingThenLoadedThenPlaying() {
        val m = MpvEventMapper()
        m.onLoadRequested("/x/film.mkv")
        assertEquals(listOf<PlayerEvent>(PlayerEvent.StateChanged(PlayerState.LOADING)), m.onEvent(start))
        assertEquals(
            listOf(PlayerEvent.Loaded("/x/film.mkv"), PlayerEvent.StateChanged(PlayerState.PLAYING)),
            m.onEvent(loadedEv),
        )
        assertEquals(listOf<PlayerEvent>(PlayerEvent.StateChanged(PlayerState.PAUSED)), m.onPause(true))
    }

    @Test
    fun endFileWithoutLoadedIsErrorOnlyOnce() {
        val m = MpvEventMapper()
        m.onEvent(start)
        val ev = m.onEvent(end)
        assertTrue(ev[0] is PlayerEvent.Error)
        assertEquals(PlayerEvent.StateChanged(PlayerState.ERROR), ev[1])
        assertTrue(m.onEvent(end).isEmpty())
    }

    @Test
    fun replacingLoadedFileDoesNotReportError() {
        val m = MpvEventMapper()
        m.onEvent(start); m.onEvent(loadedEv)
        assertTrue(m.onEvent(end).isEmpty())
        assertEquals(listOf<PlayerEvent>(PlayerEvent.StateChanged(PlayerState.LOADING)), m.onEvent(start))
    }

    @Test
    fun pauseAndPositionIgnoredBeforeLoadAndPauseCarriesIntoLoaded() {
        val m = MpvEventMapper()
        assertTrue(m.onPause(true).isEmpty())
        assertTrue(m.onTimePos(3).isEmpty())
        m.onEvent(start)
        assertEquals(PlayerEvent.StateChanged(PlayerState.PAUSED), m.onEvent(loadedEv)[1])
    }

    @Test
    fun positionUsesMillisecondsAndDuration() {
        val m = MpvEventMapper()
        m.onEvent(start); m.onEvent(loadedEv)
        m.onDuration(120)
        assertEquals(listOf<PlayerEvent>(PlayerEvent.Position(5_000L, 120_000L)), m.onTimePos(5))
    }

    @Test
    fun eofEmitsEndedOnce() {
        val m = MpvEventMapper()
        m.onEvent(start); m.onEvent(loadedEv)
        assertTrue(m.onEof(false).isEmpty())
        assertEquals(listOf(PlayerEvent.Ended, PlayerEvent.StateChanged(PlayerState.ENDED)), m.onEof(true))
        assertTrue(m.onEof(true).isEmpty())
    }
}
