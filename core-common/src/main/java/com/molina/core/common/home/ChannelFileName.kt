package com.molina.core.common.home

/**
 * Nama berkas aman untuk daftar M3U yang diimpor dari pemilih berkas. Murni (tanpa Android) supaya bisa dites JVM.
 * Hanya huruf, angka, titik, garis bawah, dan strip; tanpa jalur; ekstensi selalu .m3u atau .m3u8.
 */
object ChannelFileName {
    const val MAX_BASE = 60
    const val FALLBACK = "daftar"
    private val UNSAFE = Regex("[^A-Za-z0-9._-]+")

    fun of(display: String?): String {
        val raw = display.orEmpty().substringAfterLast('/').substringAfterLast('\\').trim()
        val ext = if (raw.endsWith(".m3u8", ignoreCase = true)) "m3u8" else "m3u"
        val base = raw.substringBeforeLast('.', raw)
            .replace(UNSAFE, "-")
            .trim('-', '.', '_')
            .take(MAX_BASE)
            .trim('-', '.', '_')
            .ifEmpty { FALLBACK }
        return "$base.$ext"
    }
}
