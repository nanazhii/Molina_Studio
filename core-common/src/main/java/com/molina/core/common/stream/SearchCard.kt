package com.molina.core.common.stream

import com.molina.core.common.StreamSearchItem

/** Nama sumber untuk chip kartu hasil: dari host URL halaman. Murni, tanpa Android. */
object SourceLabel {
    private val KNOWN: List<Pair<String, String>> = listOf(
        "youtube.com" to "YouTube",
        "youtu.be" to "YouTube",
        "tiktok.com" to "TikTok",
        "facebook.com" to "Facebook",
        "fb.watch" to "Facebook",
        "fb.com" to "Facebook",
    )
    private const val HOST_MAX = 20

    fun of(url: String): String {
        val host = hostOf(url) ?: return "Web"
        KNOWN.firstOrNull { (domain, _) -> host == domain || host.endsWith(".$domain") }?.let { return it.second }
        return host.removePrefix("www.").take(HOST_MAX)
    }

    private fun hostOf(url: String): String? {
        val authority = url.trim().substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#')
        return authority.substringAfterLast('@').substringBefore(':').trim().lowercase().ifEmpty { null }
    }
}

/** Isi satu kartu hasil pencarian, siap ditampilkan. Tanpa thumbnail: hemat RAM dan jaringan. */
data class SearchCard(
    val title: String,
    /** Teks chip: YouTube, TikTok, Facebook, atau host. */
    val source: String,
    /** Pengunggah dan waktu unggah digabung; null bila keduanya kosong. */
    val meta: String?,
    /** `m:ss` atau `h:mm:ss`; null bila durasi tidak diketahui. */
    val duration: String?,
) {
    companion object {
        fun of(item: StreamSearchItem): SearchCard {
            val meta = listOfNotNull(
                item.uploader?.trim()?.takeIf { it.isNotEmpty() },
                item.uploadedText?.trim()?.takeIf { it.isNotEmpty() },
            ).joinToString(" \u00B7 ").ifEmpty { null }
            return SearchCard(
                title = item.title.trim().ifEmpty { "(tanpa judul)" },
                source = SourceLabel.of(item.pageUrl),
                meta = meta,
                duration = item.durationSec?.takeIf { it > 0 }?.let(SearchLabel::duration),
            )
        }
    }
}
