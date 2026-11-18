package com.molina.core.common.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpProtocolTest {
    private val H = "https" + "://"

    private fun ok(line: String): YtDlpPayload {
        val m = YtDlpProtocol.parse(line) as YtDlpProtocol.Message.Reply
        return (m.reply as YtDlpReply.Ok).payload
    }

    @Test fun permintaanBerbentukGetDenganTab() {
        assertEquals("GET\t7\t${H}x.example/v?a=1", YtDlpProtocol.request(7, "${H}x.example/v?a=1"))
    }

    @Test fun permintaanMenolakSpasiDanKarakterKontrol() {
        for (bad in listOf("", "${H}x.example/a b", "${H}x.example/a\tb", "${H}x.example/a\nb", "${H}x.example/a\u0000b")) {
            val r = runCatching { YtDlpProtocol.request(1, bad) }
            assertTrue("harus ditolak: ${bad.length}", r.isFailure)
        }
    }

    @Test fun readyMembawaVersi() {
        assertEquals(YtDlpProtocol.Message.Ready("2026.11.17.232707"), YtDlpProtocol.parse("READY\t2026.11.17.232707"))
    }

    @Test fun okLengkap() {
        val p = ok(
            "OK\t5\thttps%3A%2F%2Fv.example%2Fa.mp4%3Fexpire%3D1795163479%26sig%3D1%253D\thttps%3A%2F%2Fv.example%2Fb.m4a" +
                "\tUser-Agent=UA%2F2&Referer=https%3A%2F%2Fwww.tiktok.com%2F&Cookie=a%3Db%3Bc%3Dd\t1795163479000\t126\tReel%20%C3%A9%C3%A8%20judul",
        )
        assertEquals("${H}v.example/a.mp4?expire=1795163479&sig=1%3D", p.url)
        assertEquals("${H}v.example/b.m4a", p.audioUrl)
        assertEquals(
            mapOf("User-Agent" to "UA/2", "Referer" to "${H}www.tiktok.com/", "Cookie" to "a=b;c=d"),
            p.headers,
        )
        assertEquals(1795163479000L, p.expiresAtMs)
        assertEquals(126L, p.durationSec)
        assertEquals("Reel \u00e9\u00e8 judul", p.title)
    }

    @Test fun okMinimalTanpaAudioHeaderDanJudul() {
        val p = ok("OK\t9\thttps%3A%2F%2Fv.example%2Fa.mp4\t\t\t\t\t")
        assertEquals("${H}v.example/a.mp4", p.url)
        assertNull(p.audioUrl); assertNull(p.expiresAtMs); assertNull(p.durationSec); assertNull(p.title)
        assertTrue(p.headers.isEmpty())
    }

    @Test fun errMemetakanKodeDanPesan() {
        val m = YtDlpProtocol.parse("ERR\t3\tLOGIN\tButuh%20login") as YtDlpProtocol.Message.Reply
        assertEquals(3L, m.id)
        assertEquals(YtDlpReply.Err("LOGIN", "Butuh login"), m.reply)
    }

    @Test fun errTanpaKodeMenjadiUnknown() {
        val m = YtDlpProtocol.parse("ERR\t3\t\t") as YtDlpProtocol.Message.Reply
        assertEquals("UNKNOWN", (m.reply as YtDlpReply.Err).code)
    }

    @Test fun barisBukanProtokolDanRusakDiabaikan() {
        assertNull(YtDlpProtocol.parse("Traceback (most recent call last):"))
        assertNull(YtDlpProtocol.parse(""))
        assertNull(YtDlpProtocol.parse("OK\t1\turl"))                       // kolom kurang
        assertNull(YtDlpProtocol.parse("OK\tx\ta\t\t\t\t\t"))               // id bukan angka
        assertNull(YtDlpProtocol.parse("OK\t1\t\t\t\t\t\t"))                // url kosong
        assertNull(YtDlpProtocol.parse("ERR\tx\tLOGIN\t"))
        assertNull(YtDlpProtocol.parse("READY\t%ZZ"))                       // percent-encoding rusak
    }

    @Test fun akhirBarisCrlfDitoleransi() {
        assertTrue(YtDlpProtocol.parse("READY\t1.0\r\n") is YtDlpProtocol.Message.Ready)
    }

    @Test fun nilaiDenganPlusTetapPlus() {
        val p = ok("OK\t1\thttps%3A%2F%2Fx.example%2Fa%2Bb.mp4\t\t\t\t\t")
        assertEquals("${H}x.example/a+b.mp4", p.url)
    }
}
