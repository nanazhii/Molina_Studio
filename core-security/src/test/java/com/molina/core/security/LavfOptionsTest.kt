package com.molina.core.security

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LavfOptionsTest {
    private var p: LocalShieldProxy? = null
    @After fun tutup() { p?.stop() }

    @Test fun awalanPanjangTetapMemakaiByteUtf8() {
        assertEquals("%3%a,b", LavfOptions.fixed("a,b"))
        assertEquals("%2%\u00e9", LavfOptions.fixed("\u00e9"))
    }

    @Test fun tanpaShieldHanyaBlacklistDasar() {
        assertEquals("protocol_blacklist=" + LavfOptions.fixed(LavfOptions.PROTOCOL_BLACKLIST), LavfOptions.forShield(null))
    }

    @Test fun shieldHidupMenambahkanProxy() {
        val sh = LocalShieldProxy().also { assertTrue(it.start()); p = it }
        val v = LavfOptions.forShield(sh)
        assertTrue(v.contains("http_proxy=%"))
        assertTrue(v.contains(sh.endpoint()!!.url()))
        assertFalse(v.contains("protocol_whitelist"))
    }

    @Test fun failClosedHanyaMengizinkanProtokolLokal() {
        val sh = LocalShieldProxy().also { p = it }   // belum start
        val v = LavfOptions.forShield(sh)
        assertTrue(v.contains("protocol_whitelist=" + LavfOptions.fixed(LavfOptions.LOCAL_ONLY_WHITELIST)))
        assertFalse(v.contains("http_proxy"))
    }

    @Test fun blokirRtmpMenambahBlacklist() {
        val sh = LocalShieldProxy(ShieldConfig(directStreams = DirectStreamPolicy.BLOCK)).also { p = it }
        assertTrue(LavfOptions.forShield(sh).contains("rtmpts"))
    }

    @Test fun childProcessEnv() {
        assertTrue(ChildProcessProxy.env(null).isEmpty())
        val sh = LocalShieldProxy().also { assertTrue(it.start()); p = it }
        val env = ChildProcessProxy.env(sh.endpoint())
        assertEquals(sh.endpoint()!!.url(), env["https_proxy"])
        assertEquals("", env["no_proxy"])
        sh.updateConfig(ShieldConfig(enabled = false))
        assertTrue(ChildProcessProxy.env(sh.endpoint()).isEmpty())
        assertNull(sh.endpoint())
    }
}
