// molina-28b
package com.molina.core.ui

/**
 * [molina-28b] Ukuran baris pohon drawer berkas: satu sumber untuk Terminal dan Berkas.
 * COMPACT = daftar rapat satu baris (mode lama), STANDARD = satu baris lega (Terminal sekarang),
 * TWO_LINE = nama + keterangan (Berkas sekarang; Terminal bisa memakainya kelak).
 */
object DrawerMetrics {
    const val ROW_COMPACT_DP = 36
    const val ROW_STANDARD_DP = 44
    const val ROW_TWO_LINE_DP = 56

    const val ICON_COMPACT_DP = 20
    const val ICON_STANDARD_DP = 30

    const val NAME_COMPACT_SP = 13f
    const val NAME_STANDARD_SP = 15f
    const val META_SP = 11f
}
