package com.molina.core.common.stream

import com.molina.core.common.ResolvedStream
import com.molina.core.common.StreamError
import com.molina.core.common.StreamOutcome
import com.molina.core.common.StreamResolver
import com.molina.core.common.StreamSearchItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.IOException

/**
 * Resolver cadangan ringan untuk halaman web biasa, murni Kotlin: satu permintaan HTTP, lalu
 * (a) bila Content-Type sudah media (URL tanpa ekstensi, radio, CDN) putar URL akhirnya, atau
 * (b) cari URL media di HTML lewat [HtmlMediaExtractor]. Tanpa Python/JS, jadi RAM puncaknya kecil.
 * Tidak menemukan media = UNSUPPORTED, sehingga rantai bisa lanjut ke resolver yang lebih berat.
 * [validate] dipakai untuk [canResolve] (mis. UrlPolicy): URL jaringan lokal tidak pernah diambil.
 */
class GenericPageResolver(
    private val fetcher: PageFetcher,
    private val validate: (String) -> Boolean = { true },
    private val maxBodyBytes: Int = 1_048_576,
) : StreamResolver {
    override val id: String = "generic"

    override fun canResolve(url: String): Boolean = MediaUrl.isHttp(url) && validate(url)

    override suspend fun resolve(pageUrl: String): StreamOutcome<ResolvedStream> {
        val res = try {
            runInterruptible(Dispatchers.IO) { fetcher.fetch(pageUrl, maxBodyBytes) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return StreamOutcome.Failure(StreamError.NETWORK, e.javaClass.simpleName)
        } catch (e: Exception) {
            return StreamOutcome.Failure(StreamError.UNKNOWN, e.javaClass.simpleName)
        }

        when (res.status) {
            404, 410 -> return StreamOutcome.Failure(StreamError.NOT_FOUND, "http ${res.status}")
            401 -> return StreamOutcome.Failure(StreamError.PRIVATE, "http 401")
            451 -> return StreamOutcome.Failure(StreamError.GEO_BLOCKED, "http 451")
            429, in 500..599 -> return StreamOutcome.Failure(StreamError.NETWORK, "http ${res.status}")
            !in 200..299 -> return StreamOutcome.Failure(StreamError.UNSUPPORTED, "http ${res.status}")
        }

        if (MediaUrl.isMediaContentType(res.contentType)) {
            return StreamOutcome.Success(ResolvedStream(url = res.finalUrl))
        }
        val body = res.body ?: return StreamOutcome.Failure(StreamError.UNSUPPORTED, "bukan halaman teks")
        val parsed = HtmlMediaExtractor.extract(body, res.finalUrl)
        val media = parsed.mediaUrl ?: return StreamOutcome.Failure(StreamError.UNSUPPORTED, "tidak ada media di halaman")
        return StreamOutcome.Success(
            ResolvedStream(url = media, headers = mapOf("Referer" to res.finalUrl), title = parsed.title),
        )
    }

    override suspend fun search(query: String, limit: Int): StreamOutcome<List<StreamSearchItem>> =
        StreamOutcome.Failure(StreamError.UNSUPPORTED, "tanpa pencarian")
}
