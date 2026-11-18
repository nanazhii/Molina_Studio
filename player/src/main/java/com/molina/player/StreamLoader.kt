package com.molina.player

import android.util.Log
import com.molina.core.common.FailureAction
import com.molina.core.common.StreamError
import com.molina.core.common.StreamOutcome
import com.molina.core.common.StreamResolverRegistry
import com.molina.core.security.UrlPolicy

/**
 * Menyiapkan masukan web sebelum `loadfile`: halaman situs yang dikenali resolver (dipasang :app lewat StreamResolverRegistry) diubah menjadi
 * stream langsung (plus audio terpisah dan header). Berkas lokal, tautan media langsung, dan situs
 * yang tidak dikenali diputar apa adanya. Hasil resolver wajib lolos [UrlPolicy] sebelum sampai ke mpv.
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

    private val DIRECT_EXT = setOf(
        "mp4", "m4v", "mkv", "webm", "mov", "avi", "ts", "m3u8", "mpd",
        "mp3", "m4a", "aac", "ogg", "opus", "flac", "wav",
    )

    /** True bila ini tautan halaman web (bukan berkas media langsung): layak diberi toast "memuat". */
    fun isPageUrl(raw: String): Boolean {
        val scheme = UrlPolicy.scheme(raw)
        if (scheme != "http" && scheme != "https") return false
        val path = raw.substringBefore('#').substringBefore('?').lowercase()
        return DIRECT_EXT.none { path.endsWith(".$it") }
    }

    /** Panggil dari coroutine di luar main thread. Pembatalan coroutine diteruskan. */
    suspend fun prepare(raw: String): Prepared {
        if (!isPageUrl(raw)) return Prepared.Direct(raw)
        // Dipasang :app; dibuat malas di thread pemanggil ini, bukan main thread.
        val resolver = StreamResolverRegistry.get() ?: return Prepared.Direct(raw)
        if (!resolver.canResolve(raw)) return Prepared.Direct(raw)

        (UrlPolicy.check(raw) as? UrlPolicy.Verdict.Blocked)?.let { return Prepared.Blocked(it.reason) }

        return when (val out = resolver.resolve(raw)) {
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
                Prepared.Failed(out.error, out.action)
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
