// molina-patch-b
package com.molina.player.mpv

import com.molina.cinema.TrackInfo
import com.molina.cinema.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvTrackParserTest {
    private class FakeProps(
        private val ints: Map<String, Int>,
        private val strs: Map<String, String>,
    ) : MpvPropertySource {
        override fun int(name: String): Int? = ints[name]
        override fun string(name: String): String? = strs[name]
    }

    @Test
    fun emptyWhenCountMissing() {
        assertTrue(MpvTrackParser.parse(FakeProps(emptyMap(), emptyMap())).isEmpty())
    }

    @Test
    fun parsesAudioAndSubtitleAndSkipsVideo() {
        val ints = mapOf(
            "track-list/count" to 4,
            "track-list/0/id" to 1, "track-list/1/id" to 1, "track-list/2/id" to 2, "track-list/3/id" to 1,
        )
        val strs = mapOf(
            "track-list/0/type" to "video",
            "track-list/1/type" to "audio", "track-list/1/lang" to "jpn", "track-list/1/title" to "Stereo",
            "track-list/1/selected" to "yes", "track-list/1/codec" to "aac",
            "track-list/2/type" to "audio", "track-list/2/lang" to "ind", "track-list/2/selected" to "no",
            "track-list/3/type" to "sub", "track-list/3/title" to "   ", "track-list/3/selected" to "no",
        )
        val t = MpvTrackParser.parse(FakeProps(ints, strs))
        assertEquals(3, t.size)
        assertEquals(TrackInfo(1, TrackType.AUDIO, "jpn", "Stereo", true, "aac"), t[0])
        assertEquals(TrackInfo(2, TrackType.AUDIO, "ind", null, false, null), t[1])
        assertEquals(TrackInfo(1, TrackType.SUBTITLE, null, null, false, null), t[2])
    }

    @Test
    fun skipsEntriesWithoutIdOrWithUnknownType() {
        val ints = mapOf("track-list/count" to 3, "track-list/1/id" to 1, "track-list/2/id" to 5)
        val strs = mapOf(
            "track-list/0/type" to "audio",
            "track-list/1/type" to "weird",
            "track-list/2/type" to "sub",
        )
        val t = MpvTrackParser.parse(FakeProps(ints, strs))
        assertEquals(listOf(TrackInfo(5, TrackType.SUBTITLE, null, null, false, null)), t)
    }

    @Test
    fun countIsCapped() {
        val src = object : MpvPropertySource {
            override fun int(name: String): Int? = when {
                name == "track-list/count" -> 100_000
                name.endsWith("/id") -> name.split('/')[1].toInt() + 1
                else -> null
            }
            override fun string(name: String): String? = if (name.endsWith("/type")) "audio" else null
        }
        assertEquals(128, MpvTrackParser.parse(src).size)
    }

    @Test
    fun displayNameFallbacks() {
        assertEquals("Stereo · jpn", TrackInfo(1, TrackType.AUDIO, "jpn", "Stereo", true).displayName)
        assertEquals("jpn", TrackInfo(1, TrackType.AUDIO, "jpn", null, false).displayName)
        assertEquals("Komentar", TrackInfo(2, TrackType.SUBTITLE, null, "Komentar", false).displayName)
        assertEquals("Trek 3", TrackInfo(3, TrackType.SUBTITLE, null, null, false).displayName)
        assertEquals("Trek 4", TrackInfo(4, TrackType.SUBTITLE, " ", " ", false).displayName)
    }
}
