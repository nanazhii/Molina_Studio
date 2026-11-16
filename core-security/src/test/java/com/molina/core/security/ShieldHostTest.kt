package com.molina.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShieldHostTest {
    private val s = "https" + "://"
    private val h = "http" + "://"

    @Test fun hanyaHostTanpaPortPathQuery() {
        assertEquals("cdn.example.com", ShieldHost.label(s + "CDN.Example.com:8443/a/b.m3u8?token=rahasia#x"))
    }

    @Test fun userinfoTidakPernahBocor() {
        assertEquals("example.com", ShieldHost.label(h + "pengguna:sandi@example.com/x"))
    }

    @Test fun ipv6TanpaKurung() {
        assertEquals("2001:db8::1", ShieldHost.label(h + "[2001:db8::1]:8080/x"))
    }

    @Test fun rtmpJuga() {
        assertEquals("live.example.net", ShieldHost.label("rtmp" + "://live.example.net/app/stream"))
    }

    @Test fun tidakTerbacaMenghasilkanNull() {
        assertNull(ShieldHost.label("bukan url"))
        assertNull(ShieldHost.label(s))
        assertNull(ShieldHost.label(s + "/hanya-path"))
    }

    @Test fun hostPanjangDipotong() {
        val long = "a".repeat(200) + ".com"
        assertEquals(63, ShieldHost.label(s + long + "/")!!.length)
    }

    @Test fun kodeGalatTetap() {
        assertEquals(-403, SHIELD_BLOCKED_CODE)
    }
}
