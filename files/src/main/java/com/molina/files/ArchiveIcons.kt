// molina-patch26k5e:f01
package com.molina.files

import com.molina.core.storage.TreeNode
import com.molina.core.ui.FileKind
import java.util.Locale

/**
 * Ikon kecil di daftar "Lihat isi". Jenis dan warna berasal dari [FileKind] bersama (:core-ui), satu sumber dengan
 * daftar Berkas dan drawer Terminal; di sini hanya pemetaan ke lambang teks (bukan drawable, tidak ada perubahan res).
 */
internal object ArchiveIcons {
    /** Lambang folder. */
    const val FOLDER_GLYPH = "\u25B8"

    /** Lambang teks per jenis (U+FE0E memaksa gaya teks, bukan emoji berwarna). Warna pembeda jenis ada di [FileKind]. */
    fun glyph(kind: FileKind): String = when (kind) {
        FileKind.VIDEO -> "\u25B6\uFE0E"
        FileKind.AUDIO -> "\u266A"
        FileKind.IMAGE -> "\u25A3"
        FileKind.ARCHIVE -> "\u25A4"
        FileKind.SHELL, FileKind.CODE, FileKind.MARKUP, FileKind.DATA, FileKind.SUBTITLE, FileKind.TEXT -> "\u2261"
        FileKind.OTHER -> "\u00B7"
    }

    fun glyph(n: TreeNode): String = if (n.isDirectory) FOLDER_GLYPH else glyph(FileKind.of(n.name))

    /** Warna lambang: folder = [folder]; berkas = warna jenisnya, jenis tidak dikenal = [plain]. */
    fun iconColor(n: TreeNode, folder: Int, plain: Int): Int {
        if (n.isDirectory) return folder
        val kind = FileKind.of(n.name)
        return if (kind == FileKind.OTHER) plain else kind.color
    }

    /** Warna nama: folder = [folder]; berkas = [FileKind.nameColor], jenis tidak dikenal = [plain]. */
    fun nameColor(n: TreeNode, folder: Int, plain: Int): Int {
        if (n.isDirectory) return folder
        val kind = FileKind.of(n.name)
        return if (kind == FileKind.OTHER) plain else kind.nameColor
    }
}

/**
 * Pratinjau gambar sederhana: format yang bisa didecode BitmapFactory di Android 8 (sengaja lebih sempit dari
 * [FileKind.IMAGE], yang juga memuat svg/ico/heic/avif) dan pemilihan inSampleSize (murni JVM; decode ada di ArchiveSheet).
 */
internal object ImagePreview {
    /** Sisi terpanjang hasil decode tidak melebihi nilai ini. */
    const val MAX_EDGE = 2048

    private val DECODABLE = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

    fun isImage(name: String): Boolean = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in DECODABLE

    /** Pangkat dua terkecil sehingga lebar dan tinggi hasil <= [maxEdge]. Ukuran tidak valid = 1. */
    fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
        var s = 1
        while (width / s > maxEdge || height / s > maxEdge) s *= 2
        return s
    }
}
