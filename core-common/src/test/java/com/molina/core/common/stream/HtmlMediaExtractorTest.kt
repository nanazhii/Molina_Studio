package com.molina.core.common.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HtmlMediaExtractorTest {
    private val base = "https" + "://" + "site.example/page/1"

    @Test fun ogVideoRelatifProtokol() {
        val html = """<html><head><title>Judul &amp; Co</title>
            <meta property="og:video" content="//cdn.example/v/a.mp4"><meta property="og:image" content="/t.jpg"></head></html>"""
        val r = HtmlMediaExtractor.extract(html, base)
        assertEquals("https" + "://" + "cdn.example/v/a.mp4", r.mediaUrl)
        assertEquals("Judul & Co", r.title)
    }

    @Test fun ogVideoEmbedTanpaTipeDiabaikan() {
        val html = """<meta property="og:video" content="/embed/xyz">"""
        assertNull(HtmlMediaExtractor.extract(html, base).mediaUrl)
    }

    @Test fun ogVideoDenganTipeVideoDipercaya() {
        val html = """<meta property="og:video" content="/stream/42"><meta property="og:video:type" content="video/mp4">"""
        assertEquals("https" + "://" + "site.example/stream/42", HtmlMediaExtractor.extract(html, base).mediaUrl)
    }

    @Test fun videoDanSourceSertaPosterDiabaikan() {
        val html = """<video poster="/p.jpg"><source src="/m/clip.webm" type="video/webm"><source src="/m/x.jpg" type="image/jpeg"></video>"""
        val r = HtmlMediaExtractor.extract(html, base)
        assertEquals("https" + "://" + "site.example/m/clip.webm", r.mediaUrl)
        assertEquals(1, r.candidates.size)
    }

    @Test fun jsonLdContentUrl() {
        val html = """<script type="application/ld+json">{"@type":"VideoObject","contentUrl":"/media/clip.mp4"}</script>"""
        assertEquals("https" + "://" + "site.example/media/clip.mp4", HtmlMediaExtractor.extract(html, base).mediaUrl)
    }

    @Test fun urlMentahDiSkripDenganEscape() {
        val html = """<script>var cfg={"hls":"https:\/\/cdn.example\/live\/master.m3u8?token=a\u0026b=1"};</script>"""
        assertEquals("https" + "://" + "cdn.example/live/master.m3u8?token=a&b=1", HtmlMediaExtractor.extract(html, base).mediaUrl)
    }

    @Test fun progresifDidahulukanDariManifest() {
        val html = """<meta property="og:video" content="//cdn.example/master.m3u8"><video src="/v/a.mp4"></video>"""
        assertEquals("https" + "://" + "site.example/v/a.mp4", HtmlMediaExtractor.extract(html, base).mediaUrl)
    }

    @Test fun iklanDanGambarDisaring() {
        val html = """<video src="/ads/pre.mp4"></video><script>x="https:\/\/cdn.example\/sprite.mp4"</script>"""
        assertNull(HtmlMediaExtractor.extract(html, base).mediaUrl)
    }

    @Test fun mp4DiikutiEkstensiGambarTidakTerambil() {
        val html = """<script>x="https:\/\/cdn.example\/a.mp4.jpg"</script>"""
        assertNull(HtmlMediaExtractor.extract(html, base).mediaUrl)
    }

    @Test fun absoluteMenolakSkemaLain() {
        assertNull(HtmlMediaExtractor.absolute(base, "blob:abc"))
        assertNull(HtmlMediaExtractor.absolute(base, "data:video/mp4;base64,AAAA"))
        assertEquals("https" + "://" + "site.example/x/y.mp4", HtmlMediaExtractor.absolute(base, "../x/y.mp4")?.replace("/page/../", "/"))
    }
}
