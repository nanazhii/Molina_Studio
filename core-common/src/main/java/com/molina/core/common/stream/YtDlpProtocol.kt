package com.molina.core.common.stream

import java.net.URLDecoder

/** Hasil ekstraksi yt-dlp yang sudah dibersihkan: siap dipetakan ke ResolvedStream. */
data class YtDlpPayload(
    val url: String,
    /** Trek audio terpisah (stream DASH); null = [url] sudah memuat audio. */
    val audioUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val title: String? = null,
    val durationSec: Long? = null,
    val expiresAtMs: Long? = null,
)

/** Balasan satu permintaan ke worker. [Err.code] dari daftar kode di worker (mis. LOGIN, GEO, TIMEOUT). */
sealed interface YtDlpReply {
    data class Ok(val payload: YtDlpPayload) : YtDlpReply
    data class Err(val code: String, val detail: String = "") : YtDlpReply
}

/** Pelaksana ekstraksi (di aplikasi: worker Python di Debian PRoot). Di sini hanya kontraknya, jadi mudah diuji. */
interface YtDlpRunner {
    /** Cepat dan tanpa menjalankan proses: apakah mesin yt-dlp sudah terpasang. */
    fun isInstalled(): Boolean

    /** Satu ekstraksi. Pembatalan coroutine HARUS diteruskan. Galat yang diharapkan dikembalikan sebagai [YtDlpReply.Err]. */
    suspend fun extract(url: String): YtDlpReply
}

/**
 * Protokol baris antara aplikasi dan worker yt-dlp. Satu pesan per baris, kolom dipisah TAB, seluruh nilai
 * teks di-percent-encode (UTF-8), jadi TAB, baris baru, dan karakter lain di dalam nilai tidak bisa merusak bingkai.
 *
 *   masuk : `GET<TAB>id<TAB>url` | `QUIT`
 *   keluar: `READY<TAB>versi`
 *           `OK<TAB>id<TAB>url<TAB>audio<TAB>headers<TAB>kedaluwarsa_ms<TAB>durasi_dtk<TAB>judul`
 *           `ERR<TAB>id<TAB>KODE<TAB>pesan`
 *   headers: `nama=nilai&nama=nilai`, tiap nama dan nilai di-percent-encode.
 */
object YtDlpProtocol {
    const val QUIT = "QUIT"

    sealed interface Message {
        data class Ready(val version: String) : Message
        data class Reply(val id: Long, val reply: YtDlpReply) : Message
    }

    /** Baris permintaan. URL tidak boleh memuat spasi atau karakter kontrol (tanda pemisah bingkai). */
    fun request(id: Long, url: String): String {
        require(url.isNotEmpty() && url.none { it.isWhitespace() || it.isISOControl() }) { "URL tidak valid untuk protokol" }
        return "GET\t$id\t$url"
    }

    /** Urai satu baris keluaran worker; null untuk baris yang bukan protokol (derau) atau rusak. */
    fun parse(line: String): Message? {
        val f = line.trimEnd('\r', '\n').split('\t')
        return try {
            when (f.firstOrNull()) {
                "READY" -> Message.Ready(dec(f.getOrElse(1) { "" }))
                "OK" -> parseOk(f)
                "ERR" -> {
                    val id = f.getOrNull(1)?.toLongOrNull() ?: return null
                    val code = f.getOrElse(2) { "" }.ifEmpty { "UNKNOWN" }
                    Message.Reply(id, YtDlpReply.Err(code, dec(f.getOrElse(3) { "" })))
                }
                else -> null
            }
        } catch (e: IllegalArgumentException) {
            null // percent-encoding rusak
        }
    }

    private fun parseOk(f: List<String>): Message? {
        if (f.size < 8) return null
        val id = f[1].toLongOrNull() ?: return null
        val url = dec(f[2])
        if (url.isEmpty()) return null
        val payload = YtDlpPayload(
            url = url,
            audioUrl = dec(f[3]).ifEmpty { null },
            headers = parseHeaders(f[4]),
            expiresAtMs = f[5].toLongOrNull()?.takeIf { it > 0 },
            durationSec = f[6].toLongOrNull()?.takeIf { it > 0 },
            title = dec(f[7]).trim().ifEmpty { null },
        )
        return Message.Reply(id, YtDlpReply.Ok(payload))
    }

    private fun parseHeaders(field: String): Map<String, String> {
        if (field.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (pair in field.split('&')) {
            if (pair.isEmpty()) continue
            val name = dec(pair.substringBefore('=')).trim()
            if (name.isEmpty()) continue
            out[name] = dec(pair.substringAfter('=', ""))
        }
        return out
    }

    private fun dec(s: String): String = if (s.isEmpty()) "" else URLDecoder.decode(s, "UTF-8")
}
