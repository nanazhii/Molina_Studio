// molina-patch26k5g:t01
package com.molina.files

/**
 * Pemetaan nilai tag orientasi EXIF (1..8) ke koreksi putar/cermin. Murni JVM (tanpa android.*) supaya bisa
 * diuji; pembacaan tag dan Matrix ada di ArchiveSheet. Urutan terap: putar [Fix.degrees] searah jarum jam,
 * lalu cerminkan horizontal bila [Fix.flipX]. Nilai tidak dikenal (0, 9, negatif) = tanpa koreksi.
 */
internal object ExifOrientation {
    /** Nilai ExifInterface.ORIENTATION_NORMAL. */
    const val NORMAL = 1

    data class Fix(val degrees: Int, val flipX: Boolean) {
        val isIdentity: Boolean get() = degrees == 0 && !flipX

        /** Putaran 90/270 menukar lebar dan tinggi. */
        val swapsAxes: Boolean get() = degrees == 90 || degrees == 270
    }

    val IDENTITY = Fix(0, false)

    fun fix(orientation: Int): Fix = when (orientation) {
        2 -> Fix(0, true) // cermin horizontal
        3 -> Fix(180, false)
        4 -> Fix(180, true) // cermin vertikal
        5 -> Fix(90, true) // transpose
        6 -> Fix(90, false) // foto kamera potret biasa
        7 -> Fix(270, true) // transverse
        8 -> Fix(270, false)
        else -> IDENTITY
    }
}
