package com.molina.core.security

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalShieldProxyTest {
    private val h = "http" + "://"
    private val publicIp = InetAddress.getByName("93.184.216.34")

    /** Server asal palsu: mencatat kepala permintaan, membalas 200. */
    private class Origin : AutoCloseable {
        val server = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
        @Volatile var head: String = ""
        private val t = Thread {
            try {
                server.accept().use { c ->
                    val sb = StringBuilder()
                    val i = c.getInputStream()
                    while (!sb.endsWith("\r\n\r\n")) { val b = i.read(); if (b < 0) break; sb.append(b.toChar()) }
                    head = sb.toString()
                    c.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
                }
            } catch (_: Exception) { }
        }.apply { isDaemon = true; start() }
        override fun close() { server.close() }
    }

    private var proxy: LocalShieldProxy? = null

    @After fun tutup() { proxy?.stop() }

    private fun start(cfg: ShieldConfig = ShieldConfig(), resolver: (String) -> List<InetAddress> = { listOf(publicIp) }, dialer: (InetAddress, Int) -> Socket = { _, _ -> error("tidak boleh menyambung") }): LocalShieldProxy {
        val p = LocalShieldProxy(cfg, resolver = resolver, dialer = dialer)
        assertTrue(p.start())
        proxy = p
        return p
    }

    private fun exchange(p: LocalShieldProxy, request: String, auth: Boolean = true): String {
        val ep = p.endpoint()!!
        Socket("127.0.0.1", ep.port).use { s ->
            s.soTimeout = 5_000
            val cred = Base64.getEncoder().encodeToString("${ep.user}:${ep.token}".toByteArray())
            val full = request.replace("\r\n\r\n", (if (auth) "\r\nProxy-Authorization: Basic $cred" else "") + "\r\n\r\n")
            s.getOutputStream().write(full.toByteArray()); s.getOutputStream().flush()
            return s.getInputStream().readBytes().toString(Charsets.ISO_8859_1)
        }
    }

    private fun status(r: String) = r.substringBefore("\r\n").split(' ').getOrNull(1)

    @Test fun tanpaTokenDitolak407() {
        val p = start()
        assertEquals("407", status(exchange(p, "GET ${h}example.com/ HTTP/1.1\r\nHost: example.com\r\n\r\n", auth = false)))
    }

    @Test fun loopbackTujuanDiblokir403() {
        val p = start()
        assertEquals("403", status(exchange(p, "GET ${h}127.0.0.1:9/ HTTP/1.1\r\nHost: x\r\n\r\n")))
        assertEquals("url.loopback", p.recentBlocks().last().code)
        assertEquals(1, p.snapshot.value.blocked)
    }

    @Test fun pelacakDiblokirDanBisaDikecualikan() {
        val p = start()
        assertEquals("403", status(exchange(p, "CONNECT ads.doubleclick.net:443 HTTP/1.1\r\n\r\n")))
        assertEquals("tracker", p.recentBlocks().last().code)
        p.updateConfig(ShieldConfig(blockTrackers = false))
        // tidak diblokir lagi: dialer lempar error -> tidak sampai ke sini; cukup pastikan bukan blokir pelacak
        val before = p.recentBlocks().size
        runCatching { exchange(p, "CONNECT ads.doubleclick.net:443 HTTP/1.1\r\n\r\n") }
        assertEquals(before, p.recentBlocks().size)
    }

    @Test fun portSensitifDiblokir() {
        val p = start()
        assertEquals("403", status(exchange(p, "CONNECT example.com:25 HTTP/1.1\r\n\r\n")))
        assertEquals("port.sensitive", p.recentBlocks().last().code)
    }

    @Test fun dnsRebindingDiblokir() {
        val p = start(resolver = { listOf(InetAddress.getByName("127.0.0.1")) })
        assertEquals("403", status(exchange(p, "GET ${h}evil.example/ HTTP/1.1\r\nHost: evil.example\r\n\r\n")))
        assertEquals("dns.rebinding", p.recentBlocks().last().code)
    }

    @Test fun metodeSelainGetHeadDitolak() {
        val p = start()
        assertEquals("405", status(exchange(p, "POST ${h}example.com/ HTTP/1.1\r\n\r\n")))
        assertEquals("400", status(exchange(p, "GET ${h}example.com/ HTTP/1.1\r\nContent-Length: 5\r\n\r\n")))
    }

    @Test fun teruskanTanpaCookieDanKredensialProxy() {
        Origin().use { o ->
            val p = start(dialer = { ip, port ->
                assertEquals(publicIp, ip); assertEquals(80, port)
                Socket("127.0.0.1", o.server.localPort)
            })
            val r = exchange(p, "GET ${h}example.com/seg.ts HTTP/1.1\r\nHost: example.com\r\nCookie: sid=1\r\nReferer: r\r\n\r\n")
            assertEquals("200", status(r))
            assertTrue(r.endsWith("ok"))
            val head = o.head.lowercase()
            assertTrue(head.startsWith("get /seg.ts http/1.1"))
            assertFalse("cookie bocor", head.contains("cookie"))
            assertFalse("kredensial proxy bocor", head.contains("proxy-authorization"))
            assertTrue(head.contains("host: example.com"))
            assertEquals(1L, p.snapshot.value.passed)
        }
    }

    @Test fun endpointNullBilaMatiDanFailClosed() {
        val p = start()
        assertNotNull(p.endpoint())
        assertFalse(p.failClosed())
        assertFalse(p.endpoint()!!.toString().contains(p.endpoint()!!.token))
        p.updateConfig(ShieldConfig(enabled = false))
        assertNull(p.endpoint())
        assertFalse(p.failClosed())
        p.updateConfig(ShieldConfig(enabled = true))
        p.stop()
        assertTrue(p.failClosed())
        assertNull(p.endpoint())
    }

    @Test fun daftarPelacakBisaDiganti() {
        val p = start()
        p.replaceBlocklist(TrackerListLoader.merged(setOf("ads.example.net")))
        assertEquals("403", status(exchange(p, "CONNECT x.ads.example.net:443 HTTP/1.1\r\n\r\n")))
        assertEquals("tracker", p.recentBlocks().last().code)
    }
}
