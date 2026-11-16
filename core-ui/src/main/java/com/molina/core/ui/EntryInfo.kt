// molina-28b
package com.molina.core.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * [molina-28b] Teks keterangan baris drawer berkas (Berkas dan, kelak, Terminal). Murni Kotlin tanpa Android
 * supaya bisa diuji JVM. Tanggal pintar: hari ini = jam saja, kemarin = "Kemarin <jam>", lebih lama = tanggal penuh.
 */
object EntryInfo {
    /** Item yang diubah dalam jendela ini ditandai sebagai baru (aksen hijau). */
    const val FRESH_WINDOW_MS: Long = 24L * 60L * 60L * 1000L

    /** Toleransi selisih jam perangkat (modified sedikit di depan "sekarang") tetap dianggap baru. */
    private const val CLOCK_SKEW_MS: Long = 60_000L

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH.mm")
    private val FULL: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy HH.mm")

    /** Jumlah isi folder: "2 item", "Kosong" bila 0, kosong bila tidak diketahui (negatif). */
    fun countLabel(count: Int): String = when {
        count < 0 -> ""
        count == 0 -> "Kosong"
        else -> "$count item"
    }

    /** "17.29" (hari ini), "Kemarin 17.29", atau "14/11/26 08.00"; kosong bila [modified] tidak valid (<= 0). */
    fun dateLabel(
        modified: Long,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (modified <= 0L) return ""
        val t = Instant.ofEpochMilli(modified).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when (t.toLocalDate()) {
            today -> TIME.format(t)
            today.minusDays(1) -> "Kemarin " + TIME.format(t)
            else -> FULL.format(t)
        }
    }

    /** True bila [modified] jatuh dalam [FRESH_WINDOW_MS] terakhir (dihitung dari jam, bukan dari pergantian hari). */
    fun isFresh(modified: Long, now: Long = System.currentTimeMillis()): Boolean =
        modified > 0L && (now - modified) in -CLOCK_SKEW_MS..FRESH_WINDOW_MS
}
