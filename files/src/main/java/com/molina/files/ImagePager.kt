// molina-patch26k5h:t01
package com.molina.files

import kotlin.math.abs

/**
 * Penunjuk gambar di pratinjau Lihat isi: daftar jalur entri gambar (urutan tampil di daftar) dan posisi sekarang.
 * Tidak berputar (di ujung, geser lagi = tidak terjadi apa-apa). Tak berubah (next/prev mengembalikan penunjuk baru).
 * Murni JVM; decode dan pemuatan entri ada di ArchiveSheet.
 */
internal class ImagePager private constructor(val paths: List<String>, val index: Int) {
    val size: Int get() = paths.size
    val current: String get() = paths[index]

    fun next(): ImagePager? = if (index + 1 < paths.size) ImagePager(paths, index + 1) else null

    fun prev(): ImagePager? = if (index > 0) ImagePager(paths, index - 1) else null

    /** "3 / 12" (posisi 1-based). */
    fun label(): String = "${index + 1} / ${paths.size}"

    companion object {
        /** Null bila [current] tidak ada di [paths] (termasuk daftar kosong). */
        fun of(paths: List<String>, current: String): ImagePager? {
            val i = paths.indexOf(current)
            return if (i < 0) null else ImagePager(paths, i)
        }

        /**
         * Arah geser: +1 = gambar berikutnya (jari ke kiri), -1 = sebelumnya (jari ke kanan), 0 = bukan geser
         * (terlalu pendek, atau lebih condong vertikal).
         */
        fun swipe(dx: Float, dy: Float, minDist: Float): Int {
            if (abs(dx) < minDist || abs(dx) < abs(dy) * 1.5f) return 0
            return if (dx < 0f) 1 else -1
        }
    }
}
