package com.molina.core.security

/**
 * Variabel lingkungan agar proses anak (mis. yt-dlp, pip, apt di Debian PRoot) memakai Shield.
 * PRoot berbagi jaringan dengan Android, jadi 127.0.0.1 terjangkau. Token ada di lingkungan proses
 * anak (bukan argumen baris perintah); hanya proses ber-UID sama yang dapat membacanya.
 * Kosong bila Shield mati atau tidak berjalan.
 */
object ChildProcessProxy {
    fun env(endpoint: ProxyEndpoint?): Map<String, String> {
        if (endpoint == null) return emptyMap()
        val url = endpoint.url()
        return linkedMapOf(
            "http_proxy" to url, "https_proxy" to url, "HTTP_PROXY" to url, "HTTPS_PROXY" to url,
            "no_proxy" to "", "NO_PROXY" to "",
        )
    }
}
