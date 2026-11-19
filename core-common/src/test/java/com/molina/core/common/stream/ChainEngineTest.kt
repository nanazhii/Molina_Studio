package com.molina.core.common.stream

import com.molina.core.common.ResolvedStream
import com.molina.core.common.StreamError
import com.molina.core.common.StreamOutcome
import com.molina.core.common.StreamResolver
import com.molina.core.common.StreamSearchItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChainEngineTest {
    private class Fake(override val id: String, private val out: StreamOutcome<ResolvedStream>) : StreamResolver {
        override fun canResolve(url: String) = true
        override suspend fun resolve(pageUrl: String) = out
        override suspend fun search(query: String, limit: Int): StreamOutcome<List<StreamSearchItem>> =
            StreamOutcome.Failure(StreamError.UNSUPPORTED)
    }

    private fun ok(url: String, engine: String? = null) = StreamOutcome.Success(ResolvedStream(url = url, engine = engine))

    @Test fun rantaiMengisiEngineDariResolverPemenang() = runBlocking {
        val gagal = Fake("newpipe", StreamOutcome.Failure(StreamError.UNSUPPORTED))
        val menang = Fake("ytdlp", ok("U"))
        val r = ChainStreamResolver(listOf(gagal, menang)).resolve("x") as StreamOutcome.Success
        assertEquals("ytdlp", r.value.engine)
        assertEquals("yt-dlp", EngineLabel.of(r.value.engine))
    }

    @Test fun engineYangSudahAdaTidakDitimpa() = runBlocking {
        val r = ChainStreamResolver(listOf(Fake("a", ok("U", engine = "asli")))).resolve("x") as StreamOutcome.Success
        assertEquals("asli", r.value.engine)
    }

    @Test fun engineKosongBilaTanpaRantai() {
        assertNull(ResolvedStream(url = "U").engine)
    }
}
