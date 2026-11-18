package com.molina.core.common.stream

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.util.zip.GZIPOutputStream

class HttpPageFetcherTest {
    private fun withServer(block: (base: String) -> Unit) {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        fun reply(path: String, status: Int, type: String, body: ByteArray, extra: Map<String, String> = emptyMap()) {
            s.createContext(path) { ex ->
                ex.responseHeaders.add("Content-Type", type)
                extra.forEach { (k, v) -> ex.responseHeaders.add(k, v) }
                ex.sendResponseHeaders(status, body.size.toLong().let { if (it == 0L) -1 else it })
                if (body.isNotEmpty()) ex.responseBody.use { it.write(body) } else ex.close()
            }
        }
        reply("/media", 200, "video/mp4", ByteArray(2000))
        reply("/page", 200, "text/html; charset=utf-8", "<title>Halo</title>".toByteArray())
        reply("/big", 200, "text/html", ByteArray(5000) { 'a'.code.toByte() })
        reply("/redir", 302, "text/plain", ByteArray(0), mapOf("Location" to "/page"))
        reply("/gz", 200, "text/html", ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write("<title>Gz</title>".toByteArray()) } }.toByteArray(), mapOf("Content-Encoding" to "gzip"))
        reply("/png", 200, "image/png", ByteArray(100))
        reply("/missing", 404, "text/html", "x".toByteArray())
        s.start()
        try { block("http" + "://127.0.0.1:" + s.address.port) } finally { s.stop(0) }
    }

    @Test fun mediaTidakDibacaBadannya() = withServer { b ->
        val r = HttpPageFetcher({ true }).fetch("$b/media", 1000)
        assertEquals(200, r.status); assertNull(r.body); assertEquals("video/mp4", r.contentType)
    }

    @Test fun halamanDanPengalihanDanGzip() = withServer { b ->
        assertEquals("<title>Halo</title>", HttpPageFetcher({ true }).fetch("$b/page", 1000).body)
        val r = HttpPageFetcher({ true }).fetch("$b/redir", 1000)
        assertEquals("$b/page", r.finalUrl); assertEquals("<title>Halo</title>", r.body)
        assertEquals("<title>Gz</title>", HttpPageFetcher({ true }).fetch("$b/gz", 1000).body)
    }

    @Test fun badanDipotongDiBatas() = withServer { b ->
        assertEquals(100, HttpPageFetcher({ true }).fetch("$b/big", 100).body!!.length)
    }

    @Test fun nonTeksDanGalatTanpaBadan() = withServer { b ->
        assertNull(HttpPageFetcher({ true }).fetch("$b/png", 1000).body)
        val r = HttpPageFetcher({ true }).fetch("$b/missing", 1000)
        assertEquals(404, r.status); assertNull(r.body)
    }

    @Test fun validateMenolakTujuanTermasukHasilPengalihan() = withServer { b ->
        try {
            HttpPageFetcher({ !it.endsWith("/page") }).fetch("$b/redir", 1000)
            fail("harus ditolak")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("ditolak"))
        }
    }
}
