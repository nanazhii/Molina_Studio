package com.molina.core.common.stream

import com.molina.core.common.FailureAction
import com.molina.core.common.ResolvedStream
import com.molina.core.common.StreamError
import com.molina.core.common.StreamOutcome
import com.molina.core.common.StreamResolver
import com.molina.core.common.StreamSearchItem
import kotlin.coroutines.cancellation.CancellationException

/**
 * Resolver berat untuk situs yang tidak dipahami NewPipe (TikTok, Facebook) lewat yt-dlp yang berjalan di
 * Debian PRoot. Dipasang SETELAH resolver ringan di rantai. Resolver ini hanya memetakan hasil [YtDlpRunner];
 * proses, antrean, dan masa hidup worker ada di sisi aplikasi.
 *
 * Aturan galat: mesin belum terpasang -> UNSUPPORTED + INSTALL_YTDLP; butuh login -> PRIVATE + IMPORT_COOKIES;
 * kode lain dipetakan ke StreamError. Kode tak dikenal menjadi PARSE supaya rantai boleh mencoba cadangan.
 * [StreamOutcome.Failure.detail] tidak pernah memuat URL.
 */
class YtDlpResolver(
    private val runner: YtDlpRunner,
    private val hosts: Set<String> = DEFAULT_HOSTS,
    private val validate: (String) -> Boolean = { true },
) : StreamResolver {
    override val id: String = "ytdlp"

    override fun canResolve(url: String): Boolean {
        val t = url.trim()
        if (!MediaUrl.isHttp(t) || MediaUrl.hasMediaExtension(t) || !validate(t)) return false
        val host = hostOf(t) ?: return false
        return hosts.any { host == it || host.endsWith(".$it") }
    }

    override suspend fun resolve(pageUrl: String): StreamOutcome<ResolvedStream> {
        if (!runner.isInstalled()) return notInstalled()
        val reply = try {
            runner.extract(pageUrl.trim())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return StreamOutcome.Failure(StreamError.UNKNOWN, "ytdlp: ${e.javaClass.simpleName}")
        }
        return when (reply) {
            is YtDlpReply.Ok -> fromPayload(reply.payload)
            is YtDlpReply.Err -> fromError(reply)
        }
    }

    override suspend fun search(query: String, limit: Int): StreamOutcome<List<StreamSearchItem>> =
        StreamOutcome.Failure(StreamError.UNSUPPORTED, "ytdlp: tanpa pencarian")

    private fun fromPayload(p: YtDlpPayload): StreamOutcome<ResolvedStream> {
        if (!MediaUrl.isHttp(p.url) || (p.audioUrl != null && !MediaUrl.isHttp(p.audioUrl))) {
            return StreamOutcome.Failure(StreamError.PARSE, "ytdlp: hasil bukan http(s)")
        }
        return StreamOutcome.Success(
            ResolvedStream(
                url = p.url,
                audioUrl = p.audioUrl,
                headers = p.headers,
                title = TitleCleaner.clean(p.title),
                durationSec = p.durationSec,
                expiresAtMs = p.expiresAtMs,
            ),
        )
    }

    private fun fromError(e: YtDlpReply.Err): StreamOutcome<ResolvedStream> {
        val detail = "ytdlp: ${e.code}" + scrub(e.detail).let { if (it.isEmpty()) "" else " $it" }
        return when (e.code) {
            "NOT_INSTALLED" -> notInstalled()
            "LOGIN" -> StreamOutcome.Failure(StreamError.PRIVATE, detail, FailureAction.IMPORT_COOKIES)
            "PRIVATE" -> StreamOutcome.Failure(StreamError.PRIVATE, detail)
            "AGE" -> StreamOutcome.Failure(StreamError.AGE_RESTRICTED, detail)
            "GEO" -> StreamOutcome.Failure(StreamError.GEO_BLOCKED, detail)
            "NOT_FOUND" -> StreamOutcome.Failure(StreamError.NOT_FOUND, detail)
            "NETWORK", "TIMEOUT" -> StreamOutcome.Failure(StreamError.NETWORK, detail)
            "UNSUPPORTED" -> StreamOutcome.Failure(StreamError.UNSUPPORTED, detail)
            else -> StreamOutcome.Failure(StreamError.PARSE, detail)
        }
    }

    private fun notInstalled() = StreamOutcome.Failure(
        StreamError.UNSUPPORTED, "ytdlp: belum terpasang", FailureAction.INSTALL_YTDLP,
    )

    private companion object {
        val DEFAULT_HOSTS: Set<String> = setOf("tiktok.com", "facebook.com", "fb.watch", "fb.com")
        val URL_IN_TEXT = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
        const val DETAIL_MAX = 120

        /** Buang URL dan potong pesan galat sebelum masuk log. */
        fun scrub(s: String): String =
            s.replace(URL_IN_TEXT, "<url>").replace(Regex("\\s+"), " ").trim().take(DETAIL_MAX)

        /** Host huruf kecil tanpa info pengguna dan port; null bila tidak ada. */
        fun hostOf(url: String): String? {
            val authority = url.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#')
            val host = authority.substringAfterLast('@').substringBefore(':').trim().lowercase()
            return host.ifEmpty { null }
        }
    }
}
