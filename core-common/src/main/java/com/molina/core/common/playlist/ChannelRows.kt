package com.molina.core.common.playlist

import java.util.Locale

/** Satu baris "channel" di daftar putar; [number] tetap (1-based) walau daftar difilter. */
data class ChannelRow(val number: Int, val title: String, val position: Int)

/** Logika murni daftar putar bergaya TV digital. Tanpa Android, mudah dites di JVM. */
object ChannelRows {
    fun number(n: Int): String = String.format(Locale.ROOT, "%03d", n)

    fun counter(pos: Int, total: Int): String =
        String.format(Locale.ROOT, "%03d/%03d", pos, total)

    /** Angka = cocokkan nomor channel; teks = cocokkan judul tanpa membedakan huruf besar/kecil. */
    fun filter(rows: List<ChannelRow>, query: String): List<ChannelRow> {
        val q = query.trim()
        if (q.isEmpty()) return rows
        return if (q.all { it.isDigit() }) {
            rows.filter { number(it.number).contains(q) }
        } else {
            val needle = q.lowercase(Locale.ROOT)
            rows.filter { it.title.lowercase(Locale.ROOT).contains(needle) }
        }
    }

    /** Enter pada kolom cari: angka -> channel itu; teks -> hanya bila hasil tepat satu. */
    fun pickOnEnter(visible: List<ChannelRow>, query: String): ChannelRow? {
        val q = query.trim()
        return if (q.isNotEmpty() && q.all { it.isDigit() }) {
            visible.firstOrNull { it.number == q.toIntOrNull() }
        } else {
            visible.singleOrNull()
        }
    }
}
