package com.molina.core.common.stream

import com.molina.core.common.FailureAction
import com.molina.core.common.ResolvedStream
import com.molina.core.common.StreamError
import com.molina.core.common.StreamOutcome
import com.molina.core.common.StreamResolver
import com.molina.core.common.StreamSearchItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpResolverTest {
    private fun u(rest: String) = "https" + "://" + rest

    private class FakeRunner(
        var installed: Boolean = true,
        var reply: YtDlpReply = YtDlpReply.Err("PARSE"),
        var boom: Exception? = null,
    ) : YtDlpRunner {
        var calls = 0
        var lastUrl: String? = null
        override fun isInstalled() = installed
        override suspend fun extract(url: String): YtDlpReply {
            calls++; lastUrl = url
            boom?.let { throw it }
            return reply
        }
    }

    private fun failure(r: StreamOutcome<ResolvedStream>) = r as StreamOutcome.Failure

    @Test fun mengenaliTikTokDanFacebook() {
        val r = YtDlpResolver(FakeRunner())
        for (ok in listOf(
            u("vm.tiktok.com/ZSp7Y26QA/"), u("www.tiktok.com/@a/video/1"), u("vt.tiktok.com/abc"),
            u("www.facebook.com/reel/1675310380400734/?mibextid=9drbnH"), u("m.facebook.com/watch/?v=1"),
            u("fb.watch/abc/"), u("web.facebook.com/x/videos/1"), u("WWW.TikTok.com:443/@a/video/1"),
        )) assertTrue(ok, r.canResolve(ok))
    }

    @Test fun menolakSitusLainLookalikeDanMediaLangsung() {
        val r = YtDlpResolver(FakeRunner())
        for (no in listOf(
            u("www.youtube.com/watch?v=abc"), u("nottiktok.com/v"), u("tiktok.com.evil.example/v"),
            u("evil.example/?u=tiktok.com"), u("user@evil.example/tiktok.com"), u("www.tiktok.com/v.mp4"),
            "file:///sdcard/a.mp4", "tiktok.com/v", "", "ftp://tiktok.com/v",
        )) assertFalse(no, r.canResolve(no))
    }

    @Test fun kebijakanUrlDihormati() {
        assertFalse(YtDlpResolver(FakeRunner(), validate = { false }).canResolve(u("www.tiktok.com/@a/video/1")))
    }

    @Test fun belumTerpasangMemberiAksiPasangTanpaMemanggilRunner() = runBlocking {
        val run = FakeRunner(installed = false)
        val f = failure(YtDlpResolver(run).resolve(u("www.tiktok.com/v/1")))
        assertEquals(StreamError.UNSUPPORTED, f.error)
        assertEquals(FailureAction.INSTALL_YTDLP, f.action)
        assertEquals(0, run.calls)
    }

    @Test fun suksesDipetakanLengkap() = runBlocking {
        val payload = YtDlpPayload(
            url = u("v.example/a.mp4"), audioUrl = u("v.example/b.m4a"), headers = mapOf("User-Agent" to "UA"),
            title = "Judul", durationSec = 126, expiresAtMs = 1_795_163_479_000,
        )
        val run = FakeRunner(reply = YtDlpReply.Ok(payload))
        val r = YtDlpResolver(run).resolve("  " + u("vm.tiktok.com/ZS/") + " ") as StreamOutcome.Success
        assertEquals(u("vm.tiktok.com/ZS/"), run.lastUrl)
        assertEquals(
            ResolvedStream(
                url = u("v.example/a.mp4"), audioUrl = u("v.example/b.m4a"), headers = mapOf("User-Agent" to "UA"),
                title = "Judul", durationSec = 126, expiresAtMs = 1_795_163_479_000,
            ),
            r.value,
        )
    }

    @Test fun hasilBukanHttpDitolak() = runBlocking {
        for (p in listOf(YtDlpPayload(url = "file:///etc/passwd"), YtDlpPayload(url = u("v.example/a.mp4"), audioUrl = "data:xx"))) {
            val f = failure(YtDlpResolver(FakeRunner(reply = YtDlpReply.Ok(p))).resolve(u("www.tiktok.com/v/1")))
            assertEquals(StreamError.PARSE, f.error)
        }
    }

    @Test fun kodeGalatDipetakan() = runBlocking {
        fun map(code: String): StreamOutcome.Failure =
            runBlocking { failure(YtDlpResolver(FakeRunner(reply = YtDlpReply.Err(code))).resolve(u("www.tiktok.com/v/1"))) }
        assertEquals(StreamError.PRIVATE, map("LOGIN").error)
        assertEquals(FailureAction.IMPORT_COOKIES, map("LOGIN").action)
        assertEquals(StreamError.PRIVATE, map("PRIVATE").error)
        assertNull(map("PRIVATE").action)
        assertEquals(StreamError.AGE_RESTRICTED, map("AGE").error)
        assertEquals(StreamError.GEO_BLOCKED, map("GEO").error)
        assertEquals(StreamError.NOT_FOUND, map("NOT_FOUND").error)
        assertEquals(StreamError.NETWORK, map("NETWORK").error)
        assertEquals(StreamError.NETWORK, map("TIMEOUT").error)
        assertEquals(StreamError.UNSUPPORTED, map("UNSUPPORTED").error)
        assertEquals(StreamError.PARSE, map("PARSE").error)
        assertEquals(StreamError.PARSE, map("KODE_ANEH").error)
        val ni = map("NOT_INSTALLED")
        assertEquals(StreamError.UNSUPPORTED, ni.error)
        assertEquals(FailureAction.INSTALL_YTDLP, ni.action)
    }

    @Test fun detailGalatTanpaUrlDanDipotong() = runBlocking {
        val msg = "ERROR: [tiktok] 1: gagal ambil " + u("www.tiktok.com/@a/video/1?token=SECRET") + " " + "x".repeat(500)
        val f = failure(YtDlpResolver(FakeRunner(reply = YtDlpReply.Err("PARSE", msg))).resolve(u("www.tiktok.com/v/1")))
        val d = f.detail.orEmpty()
        assertFalse(d, d.contains("SECRET"))
        assertFalse(d, d.contains("tiktok.com/@a"))
        assertTrue(d, d.contains("<url>"))
        assertTrue(d.length < 160)
    }

    @Test fun exceptionRunnerMenjadiUnknownTanpaMenelanPembatalan() = runBlocking {
        val f = failure(YtDlpResolver(FakeRunner(boom = IllegalStateException("x"))).resolve(u("www.tiktok.com/v/1")))
        assertEquals(StreamError.UNKNOWN, f.error)
        val cancelled = runCatching {
            YtDlpResolver(FakeRunner(boom = kotlin.coroutines.cancellation.CancellationException("c"))).resolve(u("www.tiktok.com/v/1"))
        }
        assertTrue(cancelled.exceptionOrNull() is kotlin.coroutines.cancellation.CancellationException)
    }

    @Test fun tanpaPencarian() = runBlocking {
        val f = YtDlpResolver(FakeRunner()).search("lagu", 10) as StreamOutcome.Failure
        assertEquals(StreamError.UNSUPPORTED, f.error)
        assertNull(f.action)
    }

    private class Skip(override val id: String, val err: StreamError) : StreamResolver {
        override fun canResolve(url: String) = true
        override suspend fun resolve(pageUrl: String): StreamOutcome<ResolvedStream> = StreamOutcome.Failure(err)
        override suspend fun search(query: String, limit: Int): StreamOutcome<List<StreamSearchItem>> =
            StreamOutcome.Failure(StreamError.UNSUPPORTED)
    }

    @Test fun dalamRantaiYtDlpMenanganiSetelahResolverRinganMenyerah() = runBlocking {
        val payload = YtDlpPayload(url = u("v.example/a.mp4"))
        val events = ArrayList<String>()
        val chain = ChainStreamResolver(
            listOf(Skip("newpipe", StreamError.UNSUPPORTED), YtDlpResolver(FakeRunner(reply = YtDlpReply.Ok(payload)))),
            events::add,
        )
        assertTrue(chain.resolve(u("www.tiktok.com/@a/video/1")) is StreamOutcome.Success)
        assertEquals(listOf("newpipe: UNSUPPORTED", "ytdlp: ok"), events)
    }

    @Test fun dalamRantaiButuhLoginBerhentiDanMembawaAksiCookie() = runBlocking {
        val generic = Skip("generic", StreamError.PARSE)
        val chain = ChainStreamResolver(listOf(YtDlpResolver(FakeRunner(reply = YtDlpReply.Err("LOGIN"))), generic))
        val f = failure(chain.resolve(u("www.facebook.com/reel/1")))
        assertEquals(StreamError.PRIVATE, f.error)
        assertEquals(FailureAction.IMPORT_COOKIES, f.action)
        assertNotNull(f.detail)
    }
}
