// molina-29
package com.molina.core.ui

/**
 * [molina-29] Palet ikon pohon BERSAMA gaya Linux Mint (Mint-Y). Satu sumber untuk TreeIconView (View: Terminal dan
 * Berkas) dan MintTreeIcons (Compose: :code), jadi warna folder/kertas/lambang tidak punya salinan terpisah.
 * Jenis berkas dan warnanya ada di [FileKind]. Bentuk gambar (geometri) masih digambar tiap renderer.
 */
object TreeIconPalette {
    val FOLDER_BACK = 0xFF5F9634.toInt()
    val FOLDER_TOP = 0xFFA9D56F.toInt()
    val FOLDER_BOTTOM = 0xFF7DB345.toInt()
    val PAPER = 0xFFF5F7F9.toInt()
    val PAPER_FOLD = 0xFFD3DAE1.toInt()
    val PAPER_EDGE = 0xFF9AA5B1.toInt()
    val BADGE_GREEN = 0xFF00E676.toInt()
    val BAND_OTHER = 0xFF9AA0A8.toInt()
    val BAND_TEXT = 0xFF10141A.toInt()

    /** Kilat diagonal lembut di panel depan folder. */
    val SHEEN = 0x1FFFFFFF

    /** Kilau tipis di tepi atas panel depan folder. */
    val HIGHLIGHT = 0xAAFFFFFF.toInt()

    /** Lambang berkas = warna jenis digelapkan sebesar faktor ini. */
    const val GLYPH_DARKEN = 0.78f

    fun darken(c: Int, f: Float): Int {
        val r = ((c shr 16) and 0xFF) * f
        val g = ((c shr 8) and 0xFF) * f
        val b = (c and 0xFF) * f
        return (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }

    /** Warna lambang di badan kertas: abu tepi kertas untuk [FileKind.OTHER], selain itu warna jenis yang digelapkan. */
    fun glyphTint(kind: FileKind): Int =
        if (kind == FileKind.OTHER) PAPER_EDGE else darken(kind.color, GLYPH_DARKEN)
}
