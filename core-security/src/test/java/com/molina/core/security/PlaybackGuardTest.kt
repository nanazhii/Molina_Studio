package com.molina.core.security

import java.net.InetAddress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackGuardTest {
    private val h = "http" + "://"
    private val s = "https" + "://"
    private var shield: LocalShieldProxy? = null

    @After fun tutup() { shield?.stop() }

    private fun shield(cfg: ShieldConfig = ShieldConfig()): LocalShieldProxy =
        LocalShieldProxy(cfg).also { assertTrue(it.start()); shield = it }

    private fun code(v: GuardVerdict) = (v as GuardVerdict.Block).code

    @Test fun berkasLokalDanHttpsLolosBilaShieldHidup() {
        val sh = shield()
        val g = PlaybackGuard(sh, sh.endpoint()?.url())
        assertEquals(GuardVerdict.Allow, g.checkSync("/storage/emulated/0/Molina/cinema/a.mkv"))
        assertEquals(GuardVerdict.Allow, g.checkSync(s + "example.com/a.m3u8"))
    }

    @Test fun skemaBerbahayaSelaluDitolakWalauShieldMati() {
        val g = PlaybackGuard(null, null)
        assertEquals("url.bad_scheme", code(g.checkSync("concat:a|b")))
        assertEquals("url.loopback", code(g.checkSync(h + "127.0.0.1/")))
    }

    @Test fun failClosedMenolakSumberJarakJauh() {
        val sh = shield()
        val g = PlaybackGuard(sh, sh.endpoint()?.url())
        sh.stop()
        assertEquals("shield.down", code(g.checkSync(s + "example.com/")))
        assertEquals(GuardVerdict.Allow, g.checkSync("/storage/emulated/0/a.mkv"))
    }

    @Test fun shieldDimulaiUlangMenolakSampaiSesiBaru() {
        val sh = shield()
        val g = PlaybackGuard(sh, sh.endpoint()?.url())
        sh.stop(); assertTrue(sh.start())
        assertEquals("shield.changed", code(g.checkSync(s + "example.com/")))
        assertEquals(GuardVerdict.Allow, PlaybackGuard(sh, sh.endpoint()?.url()).checkSync(s + "example.com/"))
    }

    @Test fun shieldDimatikanPenggunaMeloloskan() {
        val sh = shield(ShieldConfig(enabled = false))
        assertEquals(GuardVerdict.Allow, PlaybackGuard(sh, null).checkSync(s + "example.com/"))
    }

    @Test fun arusLangsungSesuaiKebijakan() {
        val pre = shield(ShieldConfig())
        val g1 = PlaybackGuard(pre, pre.endpoint()?.url())
        assertEquals(GuardVerdict.Allow, g1.checkSync("rtmp://example.com/live"))
        assertTrue(g1.needsPreflight("rtmp://example.com/live"))
        assertFalse(g1.needsPreflight(s + "example.com/"))
        val blk = shield(ShieldConfig(directStreams = DirectStreamPolicy.BLOCK))
        val g2 = PlaybackGuard(blk, blk.endpoint()?.url())
        assertEquals("direct.blocked", code(g2.checkSync("rtsp://example.com/cam")))
        assertFalse(g2.needsPreflight("rtmp://example.com/live"))
    }

    @Test fun preflightMemeriksaSemuaIp() {
        val pub = InetAddress.getByName("93.184.216.34")
        val lo = InetAddress.getByName("127.0.0.1")
        val cfg = ShieldConfig()
        assertEquals(GuardVerdict.Allow, DirectStreamPreflight({ listOf(pub) }).check("rtmp://example.com/live", cfg))
        assertEquals("dns.rebinding", code(DirectStreamPreflight({ listOf(pub, lo) }).check("rtmp://evil.example/live", cfg)))
        assertEquals("port.sensitive", code(DirectStreamPreflight({ listOf(pub) }).check("rtmp://example.com:25/x", cfg)))
        assertEquals("tracker", code(DirectStreamPreflight({ listOf(pub) }).check("rtmp://ads.doubleclick.net/x", cfg)))
        assertEquals("dns.failed", code(DirectStreamPreflight({ throw java.net.UnknownHostException() }).check("rtsp://nohost.example/x", cfg)))
        assertEquals("url.loopback", code(DirectStreamPreflight({ listOf(pub) }).check("rtsp://127.0.0.1/x", cfg)))
    }

    @Test fun preflightDnsMenggantungDibatasi() {
        val t0 = System.nanoTime()
        val v = DirectStreamPreflight({ Thread.sleep(10_000); emptyList() }, timeoutMs = 300).check("rtmp://slow.example/x", ShieldConfig())
        assertEquals("dns.failed", code(v))
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 3_000)
    }
}
