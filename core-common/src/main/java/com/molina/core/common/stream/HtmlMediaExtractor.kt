package com.molina.core.common.stream

import java.net.URI

/**
 * Mencari URL media di dalam HTML halaman: meta og:video/twitter:player:stream, elemen
 * video/source/audio, JSON-LD contentUrl, dan URL .m3u8/.mpd/.mp4 mentah di skrip. Murni teks,
 * tanpa dependensi, aman diuji di JVM. Tidak mengeksekusi JavaScript.
 */
object HtmlMediaExtractor {
    data class Result(val mediaUrl: String?, val title: String?, val candidates: List<String>)

    private val META_TAG = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val MEDIA_TAG = Regex("<(?:video|source|audio)\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val ATTR = Regex("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))")
    private val CONTENT_URL = Regex("\"contentUrl\"\\s*:\\s*\"([^\"]+)\"")
    // Satu karakter URL mentah di dalam teks skrip: biasa, \/ ter-escape, atau \uXXXX ter-escape.
    private const val SEG = """(?:[^\s"'<>\\]|\\/|\\u[0-9A-Fa-f]{4})"""
    private val RAW_MEDIA = Regex(
        """https?:(?:\\?/){2}$SEG+?\.(?:m3u8|mpd|mp4|webm|m4v|mov)(?![A-Za-z0-9_-]|\.[A-Za-z0-9])(?:\?$SEG*)?""",
        RegexOption.IGNORE_CASE,
    )
    private val TITLE = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    private val OG_MEDIA = setOf(
        "og:video", "og:video:url", "og:video:secure_url",
        "og:audio", "og:audio:url", "og:audio:secure_url",
    )
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "svg", "vtt", "srt")
    private val JUNK = listOf("doubleclick", "/preroll", "sprite", "adserver", "/ads/")

    /** Ubah [ref] (absolut, //host/x, /x, atau relatif) menjadi URL http(s) absolut; null bila tidak valid. */
    fun absolute(base: String, ref: String): String? {
        val r = ref.trim()
        if (r.isEmpty()) return null
        if (MediaUrl.isHttp(r)) return r
        return try {
            URI(base).resolve(URI(r)).toString().takeIf { MediaUrl.isHttp(it) }
        } catch (_: Exception) {
            null
        }
    }

    fun extract(html: String, baseUrl: String): Result {
        val metaList = ArrayList<Pair<String, String>>()
        val metaMap = HashMap<String, String>()
        META_TAG.findAll(html).forEach { m ->
            val a = attrs(m.value)
            val key = (a["property"] ?: a["name"])?.lowercase() ?: return@forEach
            val content = a["content"]?.trim().orEmpty()
            if (content.isEmpty()) return@forEach
            metaList += key to content
            metaMap.putIfAbsent(key, content)
        }

        val found = LinkedHashSet<String>()
        fun add(ref: String, trusted: Boolean) {
            val abs = absolute(baseUrl, unescape(ref)) ?: return
            if (isJunk(abs)) return
            if (trusted || MediaUrl.hasMediaExtension(abs)) found.add(abs)
        }

        val ogType = (metaMap["og:video:type"] ?: metaMap["og:audio:type"])?.substringBefore(';')?.trim()?.lowercase()
        val ogTrusted = ogType != null &&
            (ogType.startsWith("video/") || ogType.startsWith("audio/") || ogType in MediaUrl.MANIFEST_TYPES)
        for ((k, v) in metaList) {
            when {
                k in OG_MEDIA -> add(v, trusted = ogTrusted)
                k == "twitter:player:stream" -> add(v, trusted = true)
            }
        }

        MEDIA_TAG.findAll(html).forEach { m ->
            val a = attrs(m.value)
            val src = a["src"] ?: a["data-src"] ?: return@forEach
            val type = a["type"]?.substringBefore(';')?.trim()?.lowercase()
            val typeOk = type == null || type.startsWith("video/") || type.startsWith("audio/") ||
                type in MediaUrl.MANIFEST_TYPES || type == "application/octet-stream"
            if (typeOk) add(src, trusted = true)
        }

        CONTENT_URL.findAll(html).forEach { add(it.groupValues[1], trusted = true) }
        RAW_MEDIA.findAll(html).forEach { add(it.value, trusted = false) }

        // Progresif lebih dulu (lebih ringan di ponsel), manifest adaptif sesudahnya; urutan temuan dijaga.
        val ranked = found.sortedBy { if (isManifest(it)) 1 else 0 }

        val title = (metaMap["og:title"] ?: metaMap["twitter:title"] ?: TITLE.find(html)?.groupValues?.get(1))
            ?.let(::cleanText)?.takeIf { it.isNotEmpty() }?.take(200)

        return Result(ranked.firstOrNull(), title, ranked.take(5))
    }

    internal fun unescape(s: String): String = s
        .replace("\\/", "/")
        .replace("\\u002F", "/", ignoreCase = true)
        .replace("\\u0026", "&", ignoreCase = true)
        .replace("&amp;", "&")
        .replace("&#x2F;", "/", ignoreCase = true)
        .replace("&#38;", "&")

    private fun isManifest(url: String): Boolean {
        val path = url.substringBefore('#').substringBefore('?').lowercase()
        return path.endsWith(".m3u8") || path.endsWith(".mpd")
    }

    private fun isJunk(url: String): Boolean {
        val lower = url.lowercase()
        val path = lower.substringBefore('#').substringBefore('?')
        if (IMAGE_EXT.any { path.endsWith(".$it") }) return true
        return JUNK.any { lower.contains(it) }
    }

    private fun attrs(tag: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        ATTR.findAll(tag).forEach { m ->
            val name = m.groupValues[1].lowercase()
            val value = m.groups[2]?.value ?: m.groups[3]?.value ?: m.groups[4]?.value ?: ""
            out.putIfAbsent(name, value.replace("&amp;", "&"))
        }
        return out
    }

    private fun cleanText(s: String): String = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'").replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ").trim()
}
