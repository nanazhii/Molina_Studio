package com.molina.core.common

/**
 * Kontrak resolusi URL stream. Implementasi tinggal di modul engine (mis. `:engine-newpipe`);
 * pemutar hanya mengenal tipe di berkas ini, jadi menambah engine tidak mengubah pemutar.
 *
 * URL hasil resolusi bisa kedaluwarsa (YouTube beberapa jam): panggil [StreamResolver.resolve]
 * sesaat sebelum diputar, jangan me-resolve seluruh antrean di depan.
 */
data class ResolvedStream(
    /** URL yang langsung bisa dimuat mpv. */
    val url: String,
    /** Trek audio terpisah (stream adaptif); null = [url] sudah memuat audio. Dimuat mpv lewat audio-add. */
    val audioUrl: String? = null,
    /** Header HTTP yang wajib dikirim ke [url] dan [audioUrl] (mis. User-Agent). */
    val headers: Map<String, String> = emptyMap(),
    val title: String? = null,
    val durationSec: Long? = null,
    /** Epoch ms perkiraan URL kedaluwarsa; null = tidak diketahui. */
    val expiresAtMs: Long? = null,
)

/** Satu hasil pencarian judul. [pageUrl] diteruskan ke [StreamResolver.resolve] saat dipilih. */
data class StreamSearchItem(
    val pageUrl: String,
    val title: String,
    val uploader: String? = null,
    val durationSec: Long? = null,
    val thumbnailUrl: String? = null,
    /** Teks waktu unggah mentah dari sumber (mis. "2 days ago"); null = tidak diketahui. */
    val uploadedText: String? = null,
)

enum class StreamError {
    UNSUPPORTED, NOT_FOUND, PRIVATE, AGE_RESTRICTED, GEO_BLOCKED, NETWORK, PARSE, UNKNOWN
}

/** Tindakan perbaikan yang boleh ditawarkan UI bersama galat. */
enum class FailureAction { INSTALL_YTDLP, IMPORT_COOKIES }

/** Hasil operasi resolver. Galat yang diharapkan dikembalikan sebagai [Failure], bukan exception. */
sealed interface StreamOutcome<out T> {
    data class Success<T>(val value: T) : StreamOutcome<T>

    /** [detail] untuk log; jangan isi URL lengkap atau token. */
    data class Failure(
        val error: StreamError,
        val detail: String? = null,
        val action: FailureAction? = null,
    ) : StreamOutcome<Nothing>
}

interface StreamResolver {
    /** Pengenal singkat engine, untuk log (mis. "newpipe"). */
    val id: String

    /** Cepat dan tanpa jaringan: apakah URL ini dikenali engine. */
    fun canResolve(url: String): Boolean

    /** Ubah URL halaman menjadi stream siap putar. Pembatalan coroutine HARUS diteruskan, bukan ditelan. */
    suspend fun resolve(pageUrl: String): StreamOutcome<ResolvedStream>

    /** Pencarian judul. Engine tanpa pencarian mengembalikan Failure(UNSUPPORTED). */
    suspend fun search(query: String, limit: Int = 20): StreamOutcome<List<StreamSearchItem>>
}
