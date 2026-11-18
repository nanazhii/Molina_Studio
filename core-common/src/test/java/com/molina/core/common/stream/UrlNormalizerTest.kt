package com.molina.core.common.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlNormalizerTest {
    private fun u(rest: String) = "https" + "://" + rest

    @Test fun youtuBeMenjadiWatch() {
        assertEquals(u("www.youtube.com/watch?v=dQw4w9WgXcQ&t=42"), UrlNormalizer.normalize(u("youtu.be/dQw4w9WgXcQ?si=abc&t=42")))
    }

    @Test fun shortsLiveEmbed() {
        val want = u("www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals(want, UrlNormalizer.normalize(u("www.youtube.com/shorts/dQw4w9WgXcQ")))
        assertEquals(want, UrlNormalizer.normalize(u("m.youtube.com/live/dQw4w9WgXcQ?feature=share")))
        assertEquals(want, UrlNormalizer.normalize(u("www.youtube.com/embed/dQw4w9WgXcQ")))
    }

    @Test fun mobileDanMusicDipindahkeWww() {
        assertEquals(u("www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL1"), UrlNormalizer.normalize(u("m.youtube.com/watch?v=dQw4w9WgXcQ&list=PL1&pp=x")))
        assertEquals(u("www.youtube.com/watch?v=dQw4w9WgXcQ"), UrlNormalizer.normalize(u("music.youtube.com/watch?v=dQw4w9WgXcQ&si=zz")))
    }

    @Test fun pelacakDibuangParameterLainDijaga() {
        assertEquals(u("site.example/v?id=7"), UrlNormalizer.normalize(u("site.example/v?utm_source=a&id=7&fbclid=x&gclid=y")))
        assertEquals(u("site.example/v?si=1"), UrlNormalizer.normalize(u("site.example/v?si=1"))) // si hanya dibuang di YouTube
    }

    @Test fun fragmenDijaga() {
        assertEquals(u("site.example/v#t=10"), UrlNormalizer.normalize(u("site.example/v?utm_x=1#t=10")))
    }

    @Test fun teksShareDiambilUrlnya() {
        val text = "Lihat video ini " + u("youtu.be/dQw4w9WgXcQ?si=q") + ", keren!"
        assertEquals(u("www.youtube.com/watch?v=dQw4w9WgXcQ"), UrlNormalizer.normalize(text))
        assertEquals(u("site.example/a.mp4"), UrlNormalizer.extractUrl("cek (" + u("site.example/a.mp4") + ")."))
        assertNull(UrlNormalizer.extractUrl("tanpa tautan"))
    }

    @Test fun bukanHttpDikembalikanApaAdanya() {
        assertEquals("/storage/emulated/0/My Video.mp4", UrlNormalizer.normalize("/storage/emulated/0/My Video.mp4"))
        assertEquals("content://media/external/1", UrlNormalizer.normalize("content://media/external/1"))
        assertEquals("catatan biasa tanpa url", UrlNormalizer.normalize("catatan biasa tanpa url"))
    }

    @Test fun urlBerspasiTidakDipotong() {
        val spaced = u("site.example/a b.mp4")
        assertEquals(spaced, UrlNormalizer.normalize(spaced))
    }

    @Test fun idempoten() {
        val once = UrlNormalizer.normalize(u("youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals(once, UrlNormalizer.normalize(once))
    }
}
