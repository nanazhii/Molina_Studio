package com.molina.core.common.stream

/**
 * Merapikan judul mentah dari yt-dlp sebelum tampil di Molina Cinema.
 *
 * Contoh Facebook: `204K views · 4.5K reactions | Caption lucu 😂 #fyp #viral | Nama Akun`
 * menjadi `Caption lucu`. Aturan:
 * - Segmen statistik di awal (views, reactions, ...) dibuang. Hanya bila itu ada, segmen terakhir dianggap nama
 *   pengunggah dan ikut dibuang; judul biasa yang memuat " | " tidak disentuh.
 * - Deretan hashtag di ujung dibuang; hashtag di tengah kalimat tinggal katanya.
 * - Emoji dan tanda sisa di ujung dibuang, spasi dirapatkan, panjang dipotong di batas kata.
 * - Judul yang hanya berisi hashtag tetap menghasilkan teks (tanpa tanda #), bukan kosong.
 * Fungsi murni tanpa dependensi Android supaya bisa dites di JVM.
 */
object TitleCleaner {
    const val MAX_LEN = 80

    private val SEGMENT_SEP = Regex("""\s\|\s""")
    private val STAT_SEP = Regex("""[\u00b7\u2022]""")
    private val STAT_PART = Regex(
        """\d[\d.,]*\s*(?:[KkMmBb]|rb|jt)?\s+(?:views?|reactions?|likes?|comments?|shares?|plays?|tayangan|suka|reaksi|komentar)""",
        RegexOption.IGNORE_CASE,
    )
    private val TRAILING_TAGS = Regex("""(?:(?:^|\s+)#[\p{L}\p{N}_]+)+\s*$""")
    private val INLINE_TAG = Regex("""(?<![\p{L}\p{N}_])#([\p{L}\p{N}_]+)""")
    private val TRAILING_JUNK = Regex("""[\s\p{So}\p{Sk}\uFE0F\u200D|\u00b7\u2022\-\u2013\u2014]+$""")
    private val SPACES = Regex("""\s+""")

    /** null bila [raw] null atau kosong. */
    fun clean(raw: String?): String? {
        val original = SPACES.replace(raw ?: return null, " ").trim()
        if (original.isEmpty()) return null

        var parts = original.split(SEGMENT_SEP).map { it.trim() }.filter { it.isNotEmpty() }
        var hadStats = false
        while (parts.size > 1 && isStats(parts.first())) {
            parts = parts.drop(1)
            hadStats = true
        }
        if (hadStats && parts.size >= 2) parts = parts.dropLast(1)

        var t = parts.joinToString(" | ")
        while (true) {
            val next = TRAILING_JUNK.replace(TRAILING_TAGS.replace(t, ""), "")
            if (next == t) break
            t = next
        }
        t = INLINE_TAG.replace(t) { it.groupValues[1] }.trim()
        if (t.isEmpty()) t = INLINE_TAG.replace(original) { it.groupValues[1] }.trim()
        return shorten(t).ifEmpty { null }
    }

    private fun isStats(segment: String): Boolean {
        val items = segment.split(STAT_SEP).map { it.trim() }
        return items.isNotEmpty() && items.all { STAT_PART.matches(it) }
    }

    private fun shorten(t: String): String {
        if (t.length <= MAX_LEN) return t
        var cut = t.substring(0, MAX_LEN - 1)
        if (Character.isHighSurrogate(cut.last())) cut = cut.dropLast(1)
        val space = cut.lastIndexOf(' ')
        if (space >= MAX_LEN / 2) cut = cut.substring(0, space)
        return cut.trimEnd() + "\u2026"
    }
}
