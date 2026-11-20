package com.molina.core.common.home

/**
 * Antrean putar dari daftar Saluran: saluran terpilih jadi item pertama, lalu urutan setelahnya dan berputar
 * ke awal daftar, sehingga prev/next di pemutar berarti ganti saluran. Murni; kebijakan URL disuntikkan lewat [allow].
 */
object ChannelQueue {
    /** Batas entri playlist memori agar teks memory:// tetap kecil. */
    const val MAX_ENTRIES = 1000

    /** Daftar tautan antrean; kosong bila [picked] sendiri tidak boleh diputar. Duplikat dibuang. */
    fun urls(channels: List<Channel>, picked: Channel, allow: (String) -> Boolean = { true }): List<String> {
        if (!clean(picked.url) || !allow(picked.url)) return emptyList()
        val pos = channels.indexOfFirst { it.url == picked.url }
        val ordered = if (pos < 0) listOf(picked) else channels.drop(pos) + channels.take(pos)
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (c in ordered) {
            if (out.size >= MAX_ENTRIES) break
            val u = c.url
            if (clean(u) && allow(u) && seen.add(u)) out += u
        }
        return out
    }

    private fun clean(u: String) =
        u.isNotEmpty() && !u.startsWith("#") && u.none { it == '\n' || it == '\r' || it == '\u0000' }
}
