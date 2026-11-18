package com.molina.core.common.stream

import com.molina.core.common.ResolvedStream
import com.molina.core.common.StreamError
import com.molina.core.common.StreamOutcome
import com.molina.core.common.StreamResolver
import com.molina.core.common.StreamSearchItem

/** Tautan http(s) yang jelas berkas/manifest media (.mp4, .m3u8, .mpd, ...): langsung ke mpv, tanpa jaringan. */
class DirectResolver : StreamResolver {
    override val id: String = "direct"

    override fun canResolve(url: String): Boolean = MediaUrl.isHttp(url) && MediaUrl.hasMediaExtension(url)

    override suspend fun resolve(pageUrl: String): StreamOutcome<ResolvedStream> =
        if (canResolve(pageUrl)) StreamOutcome.Success(ResolvedStream(url = pageUrl))
        else StreamOutcome.Failure(StreamError.UNSUPPORTED, "bukan tautan media langsung")

    override suspend fun search(query: String, limit: Int): StreamOutcome<List<StreamSearchItem>> =
        StreamOutcome.Failure(StreamError.UNSUPPORTED, "tanpa pencarian")
}
