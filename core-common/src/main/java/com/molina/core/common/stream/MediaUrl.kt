package com.molina.core.common.stream

/** Pemeriksaan murni (tanpa jaringan) untuk membedakan tautan media langsung dari halaman web. */
object MediaUrl {
    val MEDIA_EXT: Set<String> = setOf(
        "mp4", "m4v", "mkv", "webm", "mov", "avi", "ts", "m3u8", "mpd",
        "mp3", "m4a", "aac", "ogg", "opus", "flac", "wav",
    )

    /** Content-Type manifest streaming adaptif. */
    val MANIFEST_TYPES: Set<String> = setOf(
        "application/vnd.apple.mpegurl", "application/x-mpegurl",
        "audio/mpegurl", "audio/x-mpegurl", "application/dash+xml",
    )

    private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")

    fun scheme(url: String): String? = SCHEME.find(url.trim())?.groupValues?.get(1)?.lowercase()

    fun isHttp(url: String): Boolean = scheme(url).let { it == "http" || it == "https" }

    /** True bila path URL (tanpa query/fragmen) berakhiran ekstensi media yang dikenal. */
    fun hasMediaExtension(url: String): Boolean {
        val path = url.substringBefore('#').substringBefore('?').lowercase()
        return MEDIA_EXT.any { path.endsWith(".$it") }
    }

    /** True bila Content-Type menandakan media (atau biner umum yang bisa disniff mpv). */
    fun isMediaContentType(contentType: String?): Boolean {
        val t = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return false
        return t.startsWith("video/") || t.startsWith("audio/") || t in MANIFEST_TYPES ||
            t == "application/ogg" || t == "application/octet-stream" || t == "binary/octet-stream"
    }
}
