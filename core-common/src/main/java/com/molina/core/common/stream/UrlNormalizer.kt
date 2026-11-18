package com.molina.core.common.stream

/**
 * Merapikan masukan URL sebelum diputar: mengambil URL dari teks hasil Share, mengubah bentuk
 * pendek YouTube (youtu.be, shorts, live, embed, m., music.) menjadi tautan watch, dan membuang
 * parameter pelacak. Masukan yang bukan http(s) dikembalikan apa adanya.
 */
object UrlNormalizer {
    private val URL_IN_TEXT = Regex("""https?:(?://)[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val SPLIT = Regex(
        """^(https?):(?://)([^/?#]*)([^?#]*)(?:\?([^#]*))?(?:#(.*))?$""",
        RegexOption.IGNORE_CASE,
    )
    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{6,20}$")
    private const val TRAILING = ".,;:!?)]}>'\""

    private val TRACKING = setOf("fbclid", "gclid", "igshid", "mc_cid", "mc_eid", "yclid", "msclkid")
    private val YT_ONLY_TRACKING = setOf("si", "feature", "pp")
    private val YT_HOSTS = setOf("youtube.com", "m.youtube.com", "music.youtube.com", "youtube-nocookie.com")
    private val YT_PATH_IDS = setOf("shorts", "live", "embed", "v")

    private data class Param(val key: String, val raw: String)

    /** URL http(s) pertama di dalam teks bebas, tanpa tanda baca penutup kalimat. */
    fun extractUrl(text: String): String? {
        val m = URL_IN_TEXT.find(text) ?: return null
        return m.value.trimEnd { it in TRAILING }.ifEmpty { null }
    }

    fun normalize(raw: String): String {
        val t = raw.trim()
        val candidate = when {
            MediaUrl.isHttp(t) -> t
            t.any { it.isWhitespace() } -> extractUrl(t) ?: return raw
            else -> return raw
        }
        val m = SPLIT.matchEntire(candidate) ?: return candidate
        val scheme = m.groupValues[1].lowercase()
        var host = m.groupValues[2]
        var path = m.groupValues[3]
        val query = m.groups[4]?.value
        val fragment = m.groups[5]?.value
        var params = parseQuery(query)

        val bare = host.lowercase().removePrefix("www.")
        val isYt = bare == "youtu.be" || bare in YT_HOSTS
        if (isYt) {
            val id = youtubeVideoId(bare, path)
            if (id != null) {
                host = "www.youtube.com"
                path = "/watch"
                params = listOf(Param("v", "v=$id")) + params.filter { it.key != "v" }
            } else if (bare != "youtu.be" && (path == "/watch" || path == "/playlist")) {
                host = "www.youtube.com"
            }
        }
        params = params.filter { p ->
            p.key !in TRACKING && !p.key.startsWith("utm_") && !(isYt && p.key in YT_ONLY_TRACKING)
        }

        return buildString {
            append(scheme).append("://").append(host).append(path)
            if (params.isNotEmpty()) append('?').append(params.joinToString("&") { it.raw })
            if (fragment != null) append('#').append(fragment)
        }
    }

    private fun parseQuery(q: String?): List<Param> =
        if (q.isNullOrEmpty()) emptyList()
        else q.split('&').filter { it.isNotEmpty() }.map { Param(it.substringBefore('=').lowercase(), it) }

    private fun youtubeVideoId(bare: String, path: String): String? {
        val seg = path.trim('/').split('/')
        val id = when {
            bare == "youtu.be" -> seg.firstOrNull()
            seg.size >= 2 && seg[0] in YT_PATH_IDS -> seg[1]
            else -> null
        }
        return id?.takeIf { VIDEO_ID.matches(it) }
    }
}
