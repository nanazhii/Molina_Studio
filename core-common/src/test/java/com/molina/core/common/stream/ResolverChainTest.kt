package com.molina.core.common.stream

import com.molina.core.common.ResolvedStream
import com.molina.core.common.StreamError
import com.molina.core.common.StreamOutcome
import com.molina.core.common.StreamResolver
import com.molina.core.common.StreamSearchItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolverChainTest {
    private fun u(rest: String) = "https" + "://" + rest

    private class Fake(
        override val id: String,
        private val can: Boolean = true,
        private val out: StreamOutcome<ResolvedStream>,
    ) : StreamResolver {
        var calls = 0
        override fun canResolve(url: String) = can
        override suspend fun resolve(pageUrl: String): StreamOutcome<ResolvedStream> { calls++; return out }
        override suspend fun search(query: String, limit: Int): StreamOutcome<List<StreamSearchItem>> =
            StreamOutcome.Failure(StreamError.UNSUPPORTED)
    }

    private fun ok(url: String) = StreamOutcome.Success(ResolvedStream(url = url))
    private fun fail(e: StreamError, d: String? = null) = StreamOutcome.Failure(e, d)

    @Test fun pertamaYangBerhasilMenang() = runBlocking {
        val a = Fake("a", out = ok("A")); val b = Fake("b", out = ok("B"))
        val r = ChainStreamResolver(listOf(a, b)).resolve(u("x.example/v")) as StreamOutcome.Success
        assertEquals("A", r.value.url); assertEquals(0, b.calls)
    }

    @Test fun unsupportedLanjutKeBerikutnya() = runBlocking {
        val a = Fake("a", out = fail(StreamError.UNSUPPORTED)); val b = Fake("b", out = ok("B"))
        val events = ArrayList<String>()
        val r = ChainStreamResolver(listOf(a, b), events::add).resolve(u("x.example/v"))
        assertTrue(r is StreamOutcome.Success)
        assertEquals(listOf("a: UNSUPPORTED", "b: ok"), events)
    }

    @Test fun networkDanNotFoundBerhentiTanpaCadangan() = runBlocking {
        for (e in listOf(StreamError.NETWORK, StreamError.NOT_FOUND, StreamError.PRIVATE, StreamError.GEO_BLOCKED, StreamError.AGE_RESTRICTED)) {
            val a = Fake("a", out = fail(e)); val b = Fake("b", out = ok("B"))
            val r = ChainStreamResolver(listOf(a, b)).resolve(u("x.example/v"))
            assertEquals(e, (r as StreamOutcome.Failure).error); assertEquals(0, b.calls)
        }
    }

    @Test fun resolverYangTidakCocokDilewati() = runBlocking {
        val a = Fake("a", can = false, out = ok("A")); val b = Fake("b", out = ok("B"))
        assertEquals("B", ((ChainStreamResolver(listOf(a, b)).resolve(u("x.example/v"))) as StreamOutcome.Success).value.url)
        assertEquals(0, a.calls)
    }

    @Test fun galatTerbaikDanAksiDijaga() = runBlocking {
        val a = Fake("a", out = fail(StreamError.UNSUPPORTED, "a"))
        val b = Fake("b", out = StreamOutcome.Failure(StreamError.UNSUPPORTED, "b", com.molina.core.common.FailureAction.INSTALL_YTDLP))
        val r = ChainStreamResolver(listOf(a, b)).resolve(u("x.example/v")) as StreamOutcome.Failure
        assertEquals(com.molina.core.common.FailureAction.INSTALL_YTDLP, r.action)
    }

    @Test fun tanpaResolverCocok() = runBlocking {
        val r = ChainStreamResolver(listOf(Fake("a", can = false, out = ok("A")))).resolve(u("x.example/v"))
        assertEquals(StreamError.UNSUPPORTED, (r as StreamOutcome.Failure).error)
    }

    @Test fun directResolver() = runBlocking {
        val d = DirectResolver()
        assertTrue(d.canResolve(u("cdn.example/a/b.m3u8?x=1")))
        assertTrue(!d.canResolve(u("site.example/watch?v=1")))
        assertTrue(!d.canResolve("/sdcard/a.mp4"))
        assertEquals(u("cdn.example/b.mp4"), (d.resolve(u("cdn.example/b.mp4")) as StreamOutcome.Success).value.url)
    }

    @Test fun cacheMenyimpanSuksesSajaDanMengikutiWaktu() = runBlocking {
        var now = 1_000L
        val a = Fake("a", out = ok("A"))
        val c = CachingStreamResolver(a, maxEntries = 2, defaultTtlMs = 10_000, safetyMarginMs = 0, clock = { now })
        c.resolve("p1"); c.resolve("p1")
        assertEquals(1, a.calls)
        now += 10_001
        c.resolve("p1")
        assertEquals(2, a.calls)
        val f = Fake("f", out = fail(StreamError.NETWORK))
        val cf = CachingStreamResolver(f, clock = { now })
        cf.resolve("x"); cf.resolve("x")
        assertEquals(2, f.calls); assertEquals(0, cf.size)
    }

    @Test fun cacheLruDanKedaluwarsaDariSumber() = runBlocking {
        var now = 0L
        val a = Fake("a", out = StreamOutcome.Success(ResolvedStream(url = "A", expiresAtMs = 100_000)))
        val c = CachingStreamResolver(a, maxEntries = 2, defaultTtlMs = 1_000_000, safetyMarginMs = 60_000, clock = { now })
        c.resolve("p1"); c.resolve("p2"); c.resolve("p3")
        assertEquals(2, c.size)
        now = 50_000 // melewati expires - margin (40_000): harus resolve ulang
        c.resolve("p3")
        assertEquals(4, a.calls)
        c.clear(); assertEquals(0, c.size)
    }
}
