package com.molina.engine.newpipe

import com.molina.core.common.ResolvedStream
import com.molina.core.common.StreamError
import com.molina.core.common.StreamOutcome
import com.molina.core.common.StreamResolver
import com.molina.core.common.StreamSearchItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException

/**
 * [StreamResolver] berbasis NewPipeExtractor: resolusi langsung di perangkat, tanpa Python/JS
 * eksternal. Jaringan jalan di Dispatchers.IO lewat runInterruptible sehingga pembatalan coroutine
 * menginterupsi thread (ditambah timeout di [MolinaDownloader]). Galat yang diharapkan menjadi
 * [StreamOutcome.Failure]; pembatalan selalu diteruskan.
 *
 * [maxHeight]: batas tinggi video adaptif (default 720 agar hemat RAM dan data di ponsel).
 */
class NewPipeStreamResolver(
    languageCode: String = "id",
    countryCode: String = "ID",
    private val maxHeight: Int = 720,
) : StreamResolver {

    override val id: String = "newpipe"

    init {
        ensureInit(Localization(languageCode, countryCode), ContentCountry(countryCode))
    }

    override fun canResolve(url: String): Boolean = serviceFor(url) != null

    override suspend fun resolve(pageUrl: String): StreamOutcome<ResolvedStream> {
        val service = serviceFor(pageUrl)
            ?: return StreamOutcome.Failure(StreamError.UNSUPPORTED, "url tidak dikenali")
        return try {
            val info = runInterruptible(Dispatchers.IO) { StreamInfo.getInfo(service, pageUrl) }
            val pick = StreamPicker.pick(info, maxHeight)
                ?: return StreamOutcome.Failure(StreamError.NOT_FOUND, "tidak ada stream yang bisa diputar")
            StreamOutcome.Success(
                ResolvedStream(
                    url = pick.url,
                    audioUrl = pick.audioUrl,
                    headers = mapOf("User-Agent" to userAgentFor(pick.url)),
                    title = info.name?.takeIf { it.isNotBlank() },
                    durationSec = info.duration.takeIf { it > 0 },
                    expiresAtMs = StreamPicker.expiresAtMs(pick.url),
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive() // interupsi akibat pembatalan bukan galat jaringan
            failFrom(e)
        }
    }

    override suspend fun search(query: String, limit: Int): StreamOutcome<List<StreamSearchItem>> {
        val q = query.trim()
        if (q.isEmpty()) return StreamOutcome.Success(emptyList())
        return try {
            val items = runInterruptible(Dispatchers.IO) {
                val service = ServiceList.YouTube
                val handler = service.searchQHFactory.fromQuery(q, listOf("videos"), "")
                val first = SearchInfo.getInfo(service, handler)
                val all = ArrayList<InfoItem>(first.relatedItems.orEmpty())
                var page = first.nextPage
                var pages = 1
                // Satu halaman hanya ~20 entri campuran; lanjutkan sampai [limit] video terkumpul.
                try {
                    while (page != null && pages < MAX_SEARCH_PAGES && all.count { it is StreamInfoItem } < limit) {
                        val more = SearchInfo.getMoreItems(service, handler, page)
                        all.addAll(more.items.orEmpty())
                        page = more.nextPage
                        pages++
                    }
                } catch (e: InterruptedException) {
                    throw e
                } catch (e: Exception) {
                    // halaman lanjutan gagal: pakai hasil yang sudah terkumpul
                }
                all
            }
            val out = items.asSequence()
                .filterIsInstance<StreamInfoItem>()
                .distinctBy { it.url }
                .map { i ->
                    StreamSearchItem(
                        pageUrl = i.url,
                        title = i.name.orEmpty(),
                        uploader = i.uploaderName?.takeIf { it.isNotBlank() },
                        durationSec = i.duration.takeIf { it >= 0 },
                        thumbnailUrl = i.thumbnails.orEmpty().maxByOrNull { it.height }?.url,
                        uploadedText = i.textualUploadDate?.takeIf { it.isNotBlank() },
                    )
                }
                .take(limit)
                .toList()
            StreamOutcome.Success(out)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            failFrom(e)
        }
    }

    private fun serviceFor(url: String): StreamingService? =
        try {
            ServiceList.all().firstOrNull { it.getLinkTypeByUrl(url) == StreamingService.LinkType.STREAM }
        } catch (e: Exception) {
            null
        }

    /** URL googlevideo terikat ke klien asalnya: User-Agent harus cocok, jika tidak 403. */
    private fun userAgentFor(url: String): String = try {
        when {
            YoutubeParsingHelper.isIosStreamingUrl(url) -> YoutubeParsingHelper.getIosUserAgent(null)
            YoutubeParsingHelper.isAndroidStreamingUrl(url) -> YoutubeParsingHelper.getAndroidUserAgent(null)
            YoutubeParsingHelper.isVisionOsStreamingUrl(url) -> YoutubeParsingHelper.getVisionOsUserAgent(null)
            else -> WEB_USER_AGENT
        }
    } catch (e: Exception) {
        WEB_USER_AGENT
    }

    /** Detail hanya nama kelas galat: pesan extractor bisa memuat URL atau token. */
    private fun failFrom(e: Exception): StreamOutcome.Failure =
        StreamOutcome.Failure(map(e), e.javaClass.simpleName)

    internal companion object {
        private const val MAX_SEARCH_PAGES = 4
        const val WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
        private val lock = Any()
        private var initialized = false

        private fun ensureInit(l: Localization, c: ContentCountry) {
            synchronized(lock) {
                if (!initialized) {
                    NewPipe.init(MolinaDownloader(), l, c)
                    initialized = true
                }
            }
        }

        /** Urutan penting: subkelas lebih dulu dari induknya. */
        fun map(e: Exception): StreamError = when (e) {
            is AgeRestrictedContentException -> StreamError.AGE_RESTRICTED
            is GeographicRestrictionException -> StreamError.GEO_BLOCKED
            is PrivateContentException -> StreamError.PRIVATE
            is ContentNotAvailableException -> StreamError.NOT_FOUND
            is ContentNotSupportedException -> StreamError.UNSUPPORTED
            is SignInConfirmNotBotException -> StreamError.NETWORK
            is ParsingException -> StreamError.PARSE
            is ReCaptchaException -> StreamError.NETWORK
            is ExtractionException -> StreamError.UNKNOWN
            is IOException -> StreamError.NETWORK
            else -> StreamError.UNKNOWN
        }
    }
}
