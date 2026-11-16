package com.molina.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {
    private val h = "http" + "://"
    private val s = "https" + "://"

    private fun blocked(url: String, allowLan: Boolean = false) = UrlPolicy.check(url, allowLan) is UrlPolicy.Verdict.Blocked
    private fun reason(url: String) = (UrlPolicy.check(url) as UrlPolicy.Verdict.Blocked).reason

    @Test fun publikDiizinkan() {
        assertTrue(UrlPolicy.check(s + "example.com/a.m3u8") is UrlPolicy.Verdict.Allowed)
        assertTrue(UrlPolicy.check(h + "93.184.216.34:8080/x") is UrlPolicy.Verdict.Allowed)
    }

    @Test fun loopbackDalamBerbagaiBentukDitolak() {
        for (u in listOf("127.0.0.1", "127.1", "2130706433", "0x7f.0.0.1", "0177.0.0.1", "localhost", "foo.localhost", "[::1]", "[::ffff:127.0.0.1]", "0.0.0.0")) {
            assertTrue("harus diblokir: $u", blocked(h + u + "/"))
        }
    }

    @Test fun metadataCloudDanLinkLocalDitolak() {
        assertEquals(UrlPolicy.Reason.LOOPBACK, reason(h + "169.254.169.254/latest"))
    }

    @Test fun jaringanLokalDitolakKecualiDiizinkan() {
        for (u in listOf("192.168.1.5", "10.0.0.2", "172.16.4.4", "100.64.0.1", "nas.local", "router")) {
            assertTrue("blokir: $u", blocked(h + u + "/"))
            assertTrue("izinkan LAN: $u", !blocked(h + u + "/", allowLan = true))
        }
    }

    @Test fun skemaBerbahayaDanPenyamaranDitolak() {
        assertEquals(UrlPolicy.Reason.BAD_SCHEME, reason("ftp://example.com/x"))
        assertEquals(UrlPolicy.Reason.BAD_SCHEME, reason("concat:a|b"))
        assertEquals(UrlPolicy.Reason.BAD_SCHEME, reason("edl://x"))
        assertEquals(UrlPolicy.Reason.USERINFO, reason(h + "good.com@evil.com/"))
        assertEquals(UrlPolicy.Reason.NON_ASCII_HOST, reason(s + "g\u043E\u043Egle.com/"))
        assertEquals(UrlPolicy.Reason.CONTROL_CHARS, reason(s + "example.com/a\r\nb"))
    }

    @Test fun portTidakValidDitolak() {
        assertEquals(UrlPolicy.Reason.BAD_PORT, reason(h + "example.com:99999/"))
        assertEquals(UrlPolicy.Reason.BAD_PORT, reason(h + "example.com:80a/"))
    }

    @Test fun alamatHasilDnsDiperiksaDenganAturanSama() {
        val lo = java.net.InetAddress.getByName("127.0.0.1")
        val lan = java.net.InetAddress.getByName("192.168.0.9")
        val pub = java.net.InetAddress.getByName("93.184.216.34")
        assertTrue(ShieldPolicy.checkAddress(lo, true) is UrlPolicy.Verdict.Blocked)
        assertTrue(ShieldPolicy.checkAddress(lan, false) is UrlPolicy.Verdict.Blocked)
        assertTrue(ShieldPolicy.checkAddress(lan, true) is UrlPolicy.Verdict.Allowed)
        assertTrue(ShieldPolicy.checkAddress(pub, false) is UrlPolicy.Verdict.Allowed)
    }

    @Test fun portSensitifDitolak() {
        assertTrue(!ShieldPolicy.portAllowed(25))
        assertTrue(!ShieldPolicy.portAllowed(3306))
        assertTrue(ShieldPolicy.portAllowed(443))
        assertTrue(!ShieldPolicy.portAllowed(0))
    }
}
