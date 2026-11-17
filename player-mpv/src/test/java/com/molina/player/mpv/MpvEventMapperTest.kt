// molina-patch13e
package com.molina.player.mpv

import com.molina.cinema.PlayerEvent
import com.molina.cinema.PlayerState
import com.molina.cinema.TrackInfo
import com.molina.cinema.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue(m.onTimePos(3.0).isEmpty())
        m.onEvent(start)
        assertEquals(PlayerEvent.StateChanged(PlayerState.PAUSED), m.onEvent(loadedEv)[1])
    }

    @Test
    fun positionUsesMillisecondsAndDuration() {
        val m = MpvEventMapper()
        m.onEvent(start); m.onEvent(loadedEv)
        m.onDuration(120.0)
        assertEquals(listOf<PlayerEvent>(PlayerEvent.Position(5_000L, 120_000L)), m.onTimePos(5.0))
    }

    @Test
    fun positionKeepsSubSecondResolution() {
        val m = MpvEventMapper()
        m.onEvent(start); m.onEvent(loadedEv)
        assertEquals(listOf<PlayerEvent>(PlayerEvent.Position(1_234L, 0L)), m.onTimePos(1.234))
    }

    @Test
    fun positionThrottledByStepButSeeksPassThrough() {
        val m = MpvEventMapper()
        m.onEvent(start); m.onEvent(loadedEv)
        assertEquals(1, m.onTimePos(0.0).size)
        assertTrue(m.onTimePos(0.05).isEmpty())
        assertTrue(m.onTimePos(0.15).isEmpty())
        assertEquals(1, m.onTimePos(0.25).size)
        assertEquals(listOf<PlayerEvent>(PlayerEvent.Position(60_000L, 0L)), m.onTimePos(60.0))
        assertTrue(m.onTimePos(59.9).isEmpty())
        assertEquals(listOf<PlayerEvent>(PlayerEvent.Position(10_000L, 0L)), m.onTimePos(10.0))
    }

    @Test
    fun firstPositionAfterNewFileAlwaysEmits() {
        val m = MpvEventMapper()
        m.onEvent(start); m.onEvent(loadedEv)
        assertEquals(1, m.onTimePos(5.0).size)
        m.onEvent(start); m.onEvent(loadedEv)
        assertEquals(1, m.onTimePos(5.05).size)
    }

    @Test
    fun invalidTimePosAndDurationAreSafe() {
        val m = MpvEventMapper()
        m.onEvent(start); m.onEvent(loadedEv)
        m.onDuration(Double.NaN)
        assertTrue(m.onTimePos(Double.NaN).isEmpty())
        assertTrue(m.onTimePos(Double.POSITIVE_INFINITY).isEmpty())
        assertEquals(listOf<PlayerEvent>(PlayerEvent.Position(0L, 0L)), m.onTimePos(-1.0))
    }

    @Test
    fun eofEmitsEndedOnce() {
        val m = MpvEventMapper()
        m.onEvent(start); m.onEvent(loadedEv)
        assertTrue(m.onEof(false).isEmpty())
        assertEquals(listOf(PlayerEvent.Ended, PlayerEvent.StateChanged(PlayerState.ENDED)), m.onEof(true))
        assertTrue(m.onEof(true).isEmpty())
    }

    // molina-patch-b
    private fun trk(type: TrackType, id: Int, sel: Boolean = false) = TrackInfo(id, type, "en", null, sel)

    @Test
    fun tracksEmitOnlyWhenListChanges() {
        val m = MpvEventMapper()
        val list = listOf(trk(TrackType.AUDIO, 1, true), trk(TrackType.SUBTITLE, 1))
        assertTrue(m.onTracks(emptyList()).isEmpty())
        assertEquals(
            listOf<PlayerEvent>(PlayerEvent.TracksChanged(listOf(list[0]), listOf(list[1]))),
            m.onTracks(list),
        )
        assertTrue(m.onTracks(list.toList()).isEmpty())
        assertEquals(1, m.onTracks(listOf(trk(TrackType.AUDIO, 1), trk(TrackType.SUBTITLE, 1, true))).size)
    }

    @Test
    fun newFileClearsTracksOnce() {
        val m = MpvEventMapper()
        m.onTracks(listOf(trk(TrackType.AUDIO, 1)))
        assertEquals(
            listOf<PlayerEvent>(PlayerEvent.StateChanged(PlayerState.LOADING), PlayerEvent.TracksChanged(emptyList(), emptyList())),
            m.onEvent(start),
        )
        assertFalse(m.hasTrack(TrackType.AUDIO, 1))
        assertEquals(listOf<PlayerEvent>(PlayerEvent.StateChanged(PlayerState.LOADING)), m.onEvent(start))
    }

    @Test
    fun hasTrackChecksTypeAndId() {
        val m = MpvEventMapper()
        m.onTracks(listOf(trk(TrackType.AUDIO, 2), trk(TrackType.SUBTITLE, 1)))
        assertTrue(m.hasTrack(TrackType.AUDIO, 2))
        assertFalse(m.hasTrack(TrackType.AUDIO, 1))
        assertFalse(m.hasTrack(TrackType.SUBTITLE, 2))
        assertTrue(m.hasTrack(TrackType.SUBTITLE, 1))
    }

    @Test
    fun speedEmitsOnChangeAndIgnoresInvalid() {
        val m = MpvEventMapper()
        assertEquals(listOf<PlayerEvent>(PlayerEvent.SpeedChanged(1.0)), m.onSpeed(1.0))
        assertTrue(m.onSpeed(1.0).isEmpty())
        assertEquals(listOf<PlayerEvent>(PlayerEvent.SpeedChanged(1.5)), m.onSpeed(1.5))
        assertTrue(m.onSpeed(Double.NaN).isEmpty())
        assertTrue(m.onSpeed(Double.POSITIVE_INFINITY).isEmpty())
        assertTrue(m.onSpeed(0.0).isEmpty())
        assertTrue(m.onSpeed(-2.0).isEmpty())
    }
}
