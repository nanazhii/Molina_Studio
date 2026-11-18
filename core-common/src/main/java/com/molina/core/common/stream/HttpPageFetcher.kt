package com.molina.core.common.stream

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream

/** Hasil satu pengambilan halaman. [body] null bila isinya media, bukan teks, atau status galat. */
data class PageResponse(val finalUrl: String, val status: Int, val contentType: String?, val body: String?)

/** Pengambil halaman yang bisa diganti di uji. Dipanggil di thread latar (blocking). */
interface PageFetcher {
    @Throws(IOException::class)
    fun fetch(url: String, maxBytes: Int): PageResponse
}

/**
 * [PageFetcher] di atas HttpURLConnection (tanpa dependensi). Pengalihan diikuti manual dan SETIAP
 * tujuan diperiksa lewat [validate] (mis. UrlPolicy), sehingga pengalihan ke jaringan lokal tidak lolos.
 * Bila Content-Type sudah menunjukkan media, badan TIDAK dibaca: koneksi langsung ditutup, jadi
 * stream besar atau tanpa akhir tidak menghabiskan RAM. Badan teks dibatasi [maxBytes] (terpotong, bukan galat).
 */
class HttpPageFetcher(
    private val validate: (String) -> Boolean,
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 12_000,
    private val userAgent: String = DEFAULT_USER_AGENT,
) : PageFetcher {

    override fun fetch(url: String, maxBytes: Int): PageResponse {
        var current = url
        for (hop in 0..MAX_REDIRECTS) {
            if (!MediaUrl.isHttp(current) || !validate(current)) throw IOException("tujuan ditolak kebijakan URL")
            val conn = URL(current).openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = connectTimeoutMs
                conn.readTimeout = readTimeoutMs
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", userAgent)
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                conn.setRequestProperty("Accept-Language", "id,en;q=0.8")
                conn.setRequestProperty("Accept-Encoding", "gzip")

                val code = conn.responseCode
                if (code in 300..399 && code != 304) {
                    val loc = conn.getHeaderField("Location") ?: return PageResponse(current, code, null, null)
                    current = HtmlMediaExtractor.absolute(current, loc) ?: throw IOException("pengalihan tidak valid")
                    continue
                }
                val ct = conn.contentType
                if (code in 200..299 && MediaUrl.isMediaContentType(ct)) return PageResponse(current, code, ct, null)
                val body = if (code in 200..299 && isTextual(ct)) readBody(conn, ct, maxBytes) else null
                return PageResponse(current, code, ct, body)
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("terlalu banyak pengalihan")
    }

    private fun isTextual(ct: String?): Boolean {
        val t = ct?.lowercase() ?: return true
        return t.startsWith("text/") || "html" in t || "xml" in t || "json" in t || "javascript" in t
    }

    private fun readBody(conn: HttpURLConnection, ct: String?, maxBytes: Int): String {
        val raw = conn.inputStream
        val gzip = conn.contentEncoding?.equals("gzip", ignoreCase = true) == true
        val stream = if (gzip) GZIPInputStream(raw) else raw
        stream.use { s ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (total < maxBytes) {
                val n = s.read(buf, 0, minOf(buf.size, maxBytes - total))
                if (n < 0) break
                out.write(buf, 0, n)
                total += n
            }
            return String(out.toByteArray(), charsetOf(ct))
        }
    }

    private fun charsetOf(ct: String?): Charset {
        val name = ct?.let { CHARSET.find(it)?.groupValues?.get(1) } ?: return Charsets.UTF_8
        return try {
            Charset.forName(name)
        } catch (_: Exception) {
            Charsets.UTF_8
        }
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 8.0.0; Molina) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
        val CHARSET = Regex("charset\\s*=\\s*\"?([A-Za-z0-9_.:-]+)", RegexOption.IGNORE_CASE)
    }
}
