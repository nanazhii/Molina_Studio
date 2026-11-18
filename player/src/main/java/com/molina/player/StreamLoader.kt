package com.molina.player

import android.util.Log
import com.molina.core.common.FailureAction
import com.molina.core.common.StreamError
import com.molina.core.common.StreamSearchItem
import com.molina.core.common.StreamOutcome
import com.molina.core.common.StreamResolverRegistry
import com.molina.core.common.stream.MediaUrl
import com.molina.core.common.stream.UrlNormalizer
import com.molina.core.security.UrlPolicy

/**
 * Menyiapkan masukan web sebelum `loadfile` (molina-wave2). Urutan kerja:
 * 1. [UrlNormalizer]: URL diambil dari teks Share, bentuk pendek YouTube dirapikan, pelacak dibuang.
 * 2. Berkas lokal dan tautan media langsung (.mp4, .m3u8, ...) diputar apa adanya.
 * 3. Halaman web diserahkan ke resolver terpasang (rantai di :app lewat StreamResolverRegistry).
 * 4. Bila rantai menyerah (UNSUPPORTED/PARSE) tanpa saran perbaikan, mpv diberi kesempatan memutar
 *    sendiri; ini mempertahankan perilaku lama untuk URL yang hanya dipahami ffmpeg/mpv.
 * Hasil resolver wajib lolos [UrlPolicy] sebelum sampai ke mpv.
 */
internal object StreamLoader {
    private const val TAG = "Molina/resolver"

    sealed interface Prepared {
        /** Putar apa adanya. */
        data class Direct(val url: String) : Prepared

        data class Ready(
            val url: String,
            val audioUrl: String?,
            val headers: Map<String, String>,
            val title: String?,
        ) : Prepared

        data class Failed(val error: StreamError, val action: FailureAction?) : Prepared

        data class Blocked(val reason: UrlPolicy.Reason) : Prepared
    }

    // --- molina-wave3: pencarian judul ---

    /** Awalan internal: teks bukan-tautan dari dialog Buka URL dibawa ke MPVActivity sebagai permintaan cari. */
    const val SEARCH_PREFIX = "molina-search:"
    private const val SEARCH_LIMIT = 15

    sealed interface Searched {
        data class Results(val items: List<StreamSearchItem>) : Searched

        data object Empty : Searched

        data class Failed(val error: StreamError) : Searched
    }

    fun searchRequest(text: String): String = SEARCH_PREFIX + text.trim()

    fun isSearch(raw: String): Boolean = raw.startsWith(SEARCH_PREFIX)

    fun searchQuery(raw: String): String = raw.removePrefix(SEARCH_PREFIX).trim()

    /** True bila teks layak dicari sebagai judul: bukan jalur berkas dan tidak memuat "://" (tautan salah skema). */
    fun looksLikeQuery(text: String): Boolean {
        val t = text.trim()
        return t.length >= 2 && !t.startsWith("/") && !t.contains("://")
    }

    /** Panggil dari coroutine di luar main thread. Hanya hasil http(s) yang lolos [UrlPolicy] diteruskan. */
    suspend fun search(query: String): Searched {
        val resolver = StreamResolverRegistry.get() ?: return Searched.Failed(StreamError.UNSUPPORTED)
        return when (val out = resolver.search(query, SEARCH_LIMIT)) {
            is StreamOutcome.Success -> {
                val items = out.value.filter {
                    MediaUrl.isHttp(it.pageUrl) && UrlPolicy.check(it.pageUrl) is UrlPolicy.Verdict.Allowed
                }
                if (items.isEmpty()) Searched.Empty else Searched.Results(items)
            }
            is StreamOutcome.Failure -> {
                Log.w(TAG, "search: ${out.error} ${out.detail.orEmpty()}")
                Searched.Failed(out.error)
            }
        }
    }

    /** True bila ini tautan halaman web (bukan berkas media langsung): layak diberi toast "memuat". */
    fun isPageUrl(raw: String): Boolean {
        val url = UrlNormalizer.normalize(raw)
        return MediaUrl.isHttp(url) && !MediaUrl.hasMediaExtension(url)
    }

    /** Panggil dari coroutine di luar main thread. Pembatalan coroutine diteruskan. */
    suspend fun prepare(raw: String): Prepared {
        val url = UrlNormalizer.normalize(raw)
        if (!isPageUrl(url)) return Prepared.Direct(url)
        // Dipasang :app; dibuat malas di thread pemanggil ini, bukan main thread.
        val resolver = StreamResolverRegistry.get() ?: return Prepared.Direct(url)
        if (!resolver.canResolve(url)) return Prepared.Direct(url)

        (UrlPolicy.check(url) as? UrlPolicy.Verdict.Blocked)?.let { return Prepared.Blocked(it.reason) }

        return when (val out = resolver.resolve(url)) {
            is StreamOutcome.Success -> {
                val s = out.value
                (UrlPolicy.check(s.url) as? UrlPolicy.Verdict.Blocked)?.let { return Prepared.Blocked(it.reason) }
                s.audioUrl?.let { a ->
                    (UrlPolicy.check(a) as? UrlPolicy.Verdict.Blocked)?.let { return Prepared.Blocked(it.reason) }
                }
                Prepared.Ready(s.url, s.audioUrl, s.headers, s.title)
            }
            is StreamOutcome.Failure -> {
                Log.w(TAG, "${resolver.id}: ${out.error} ${out.detail.orEmpty()}")
                val giveUp = (out.error == StreamError.UNSUPPORTED || out.error == StreamError.PARSE) && out.action == null
                if (giveUp) Prepared.Direct(url) else Prepared.Failed(out.error, out.action)
            }
        }
    }

    /** Argumen `loadfile` dengan header per-entri (butuh mpv >= 0.38 untuk argumen indeks). */
    fun loadfileArgs(url: String, flag: String, headers: Map<String, String>): Array<String> {
        val opt = headerOption(headers) ?: return arrayOf("loadfile", url, flag)
        return arrayOf("loadfile", url, flag, "-1", opt)
    }

    /**
     * `http-header-fields=...` dengan awalan panjang-tetap `%N%` (N = jumlah byte) agar koma dan
     * titik dua di dalam nilai tidak dibaca sebagai pemisah. Berlapis: tiap header, lalu seluruh daftar.
     */
    fun headerOption(headers: Map<String, String>): String? {
        val clean = headers.filter { (k, v) ->
            k.isNotBlank() && k.none { it == ':' || it == '\r' || it == '\n' } && v.none { it == '\r' || it == '\n' }
        }
        if (clean.isEmpty()) return null
        val list = clean.entries.joinToString(",") { fixed("${it.key}: ${it.value}") }
        return "http-header-fields=" + fixed(list)
    }

    private fun fixed(s: String): String = "%${s.toByteArray(Charsets.UTF_8).size}%$s"
}
