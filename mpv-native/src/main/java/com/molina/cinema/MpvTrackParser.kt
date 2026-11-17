// molina-patch-b
package com.molina.cinema

/** Sumber properti mpv. Dipisah dari MPVLib agar parser murni dan bisa diuji di JVM. */
internal interface MpvPropertySource {
    fun int(name: String): Int?
    fun string(name: String): String?
}

/** Membaca properti track-list per trek menjadi TrackInfo. Hanya trek audio dan subtitle; video diabaikan. */
internal object MpvTrackParser {
    private const val MAX_TRACKS = 128

    fun parse(src: MpvPropertySource): List<TrackInfo> {
        val count = (src.int("track-list/count") ?: 0).coerceIn(0, MAX_TRACKS)
        val out = ArrayList<TrackInfo>(count)
        for (i in 0 until count) {
            val type = when (src.string("track-list/$i/type")) {
                "audio" -> TrackType.AUDIO
                "sub" -> TrackType.SUBTITLE
                else -> continue
            }
            val id = src.int("track-list/$i/id") ?: continue
            out += TrackInfo(
                id = id,
                type = type,
                lang = clean(src.string("track-list/$i/lang")),
                title = clean(src.string("track-list/$i/title")),
                selected = src.string("track-list/$i/selected") == "yes",
                codec = clean(src.string("track-list/$i/codec")),
            )
        }
        return out
    }

    private fun clean(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() }
}
