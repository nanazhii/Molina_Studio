package com.molina.core.common.stream

import com.molina.core.common.StreamError
import com.molina.core.common.StreamOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GenericPageResolverTest {
    private fun u(rest: String) = "https" + "://" + rest
    private val page = u("site.example/p/1")

    private fun resolver(resp: () -> PageResponse, validate: (String) -> Boolean = { true }) =
        GenericPageResolver(object : PageFetcher { override fun fetch(url: String, maxBytes: Int) = resp() }, validate)

    private fun html(body: String, status: Int = 200) = PageResponse(page, status, "text/html; charset=utf-8", body)

    @Test fun kontenMediaLangsungDariContentType() = runBlocking {
        val r = resolver({ PageResponse(u("radio.example/live"), 200, "audio/mpeg", null) }).resolve(page)
        assertEquals(u("radio.example/live"), (r as StreamOutcome.Success).value.url)
    }

    @Test fun mediaDiHtmlDenganRefererDanJudul() = runBlocking {
        val r = resolver({ html("""<title>Film</title><video src="/m/a.mp4"></video>""") }).resolve(page) as StreamOutcome.Success
        assertEquals(u("site.example/m/a.mp4"), r.value.url)
        assertEquals(page, r.value.headers["Referer"]); assertEquals("Film", r.value.title)
    }

    @Test fun tanpaMediaUnsupported() = runBlocking {
        val r = resolver({ html("<p>halo</p>") }).resolve(page) as StreamOutcome.Failure
        assertEquals(StreamError.UNSUPPORTED, r.error)
    }

    @Test fun pemetaanStatus() = runBlocking {
        fun err(code: Int) = (resolver({ html("", code) }).let { runBlocking { it.resolve(page) } } as StreamOutcome.Failure).error
        assertEquals(StreamError.NOT_FOUND, err(404)); assertEquals(StreamError.NOT_FOUND, err(410))
        assertEquals(StreamError.PRIVATE, err(401)); assertEquals(StreamError.UNSUPPORTED, err(403))
        assertEquals(StreamError.GEO_BLOCKED, err(451)); assertEquals(StreamError.NETWORK, err(503))
        assertEquals(StreamError.NETWORK, err(429))
    }

    @Test fun ioExceptionMenjadiNetwork() = runBlocking {
        val r = resolver({ throw IOException("x") }).resolve(page) as StreamOutcome.Failure
        assertEquals(StreamError.NETWORK, r.error)
    }

    @Test fun validateMenentukanCanResolve() {
        val r = resolver({ html("") }, validate = { !it.contains("lan.example") })
        assertTrue(r.canResolve(page)); assertFalse(r.canResolve(u("lan.example/x"))); assertFalse(r.canResolve("/sdcard/a.mp4"))
    }
}
