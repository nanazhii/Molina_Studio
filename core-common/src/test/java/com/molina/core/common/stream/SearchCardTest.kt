package com.molina.core.common.stream

import com.molina.core.common.StreamSearchItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchCardTest {
    private fun u(rest: String) = "https" + "://" + rest

    @Test fun sumberDariHost() {
        assertEquals("YouTube", SourceLabel.of(u("www.youtube.com/watch?v=abc")))
        assertEquals("YouTube", SourceLabel.of(u("m.youtube.com/watch?v=abc")))
        assertEquals("YouTube", SourceLabel.of(u("music.youtube.com/watch?v=abc")))
        assertEquals("YouTube", SourceLabel.of(u("youtu.be/abc")))
        assertEquals("TikTok", SourceLabel.of(u("vm.tiktok.com/ZS/")))
        assertEquals("TikTok", SourceLabel.of(u("www.tiktok.com/@a/video/1")))
        assertEquals("Facebook", SourceLabel.of(u("www.facebook.com/reel/1")))
        assertEquals("Facebook", SourceLabel.of(u("fb.watch/abc/")))
    }

    @Test fun sumberLainDanTiruanTidakDisamarkan() {
        assertEquals("cdn.example", SourceLabel.of(u("www.cdn.example/a")))
        assertEquals("nottiktok.com", SourceLabel.of(u("nottiktok.com/v")))
        assertEquals("tiktok.com.evil.example".take(20), SourceLabel.of(u("tiktok.com.evil.example/v"))) // dipotong, dan bukan "TikTok"
        assertEquals("evil.example", SourceLabel.of(u("user@evil.example/youtube.com")))
        assertEquals("a".repeat(20), SourceLabel.of(u("a".repeat(40) + ".example/x")))
        assertEquals("Web", SourceLabel.of("tidak-ada-host"))
        assertEquals("Web", SourceLabel.of(""))
    }

    @Test fun kartuLengkap() {
        val c = SearchCard.of(StreamSearchItem(u("www.youtube.com/watch?v=1"), " Judul ", " Kanal ", 3725, null, " 2 days ago "))
        assertEquals(SearchCard("Judul", "YouTube", "Kanal \u00B7 2 days ago", "1:02:05"), c)
    }

    @Test fun kartuMinimal() {
        val c = SearchCard.of(StreamSearchItem(u("www.youtube.com/watch?v=1"), "  "))
        assertEquals("(tanpa judul)", c.title)
        assertNull(c.meta)
        assertNull(c.duration)
    }

    @Test fun metaSebagianDanDurasiNol() {
        assertEquals("Kanal", SearchCard.of(StreamSearchItem(u("youtu.be/a"), "J", uploader = "Kanal", durationSec = 0)).meta)
        assertEquals("2 days ago", SearchCard.of(StreamSearchItem(u("youtu.be/a"), "J", uploader = " ", uploadedText = "2 days ago")).meta)
        assertNull(SearchCard.of(StreamSearchItem(u("youtu.be/a"), "J", durationSec = 0)).duration)
        assertEquals("0:09", SearchCard.of(StreamSearchItem(u("youtu.be/a"), "J", durationSec = 9)).duration)
    }
}
