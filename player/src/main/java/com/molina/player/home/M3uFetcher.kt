package com.molina.player.home

import com.molina.core.security.UrlPolicy
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Baca teks dari aliran dengan batas byte (terpotong, bukan galat) supaya daftar raksasa tidak menghabiskan RAM. */
internal fun InputStream.readTextLimited(maxBytes: Int): String {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8 * 1024)
    while (out.size() < maxBytes) {
        val n = read(buf, 0, minOf(buf.size, maxBytes - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toString(Charsets.UTF_8.name())
}

/**
 * Pengambil daftar M3U lewat http(s). Dibuat terpisah dari HttpPageFetcher karena itu sengaja tidak membaca
 * badan bertipe media (M3U memakai tipe seperti audio/x-mpegurl). Pengalihan diikuti manual dan SETIAP tujuan
 * diperiksa lewat [validate] (UrlPolicy), sehingga pengalihan ke jaringan lokal tidak lolos. Blocking: panggil dari IO.
 */
internal class M3uFetcher(
    private val validate: (String) -> Boolean = { UrlPolicy.check(it) is UrlPolicy.Verdict.Allowed },
) {
    @Throws(IOException::class)
    fun fetch(url: String): String {
        var current = url.trim()
        for (hop in 0..MAX_REDIRECTS) {
            if (!isHttp(current) || !validate(current)) throw IOException("tujuan ditolak kebijakan URL")
            val conn = URL(current).openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS
                conn.setRequestProperty("User-Agent", USER_AGENT)
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: throw IOException("pengalihan tanpa tujuan")
                    current = URL(URL(current), location).toString()
                    continue
                }
                if (code !in 200..299) throw IOException("status $code")
                return conn.inputStream.use { it.readTextLimited(MAX_BYTES) }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("terlalu banyak pengalihan")
    }

    companion object {
        const val MAX_BYTES = 4 * 1024 * 1024
        private const val MAX_REDIRECTS = 3
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val USER_AGENT = "Molina/1.0"

        fun isHttp(s: String): Boolean = s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true)
    }
}
