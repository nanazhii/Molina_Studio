// molina-patch26k5d
package com.molina.files

import com.molina.core.storage.TreeNode
import java.util.Locale

/** Jenis berkas untuk ikon kecil di daftar "Lihat isi". Murni JVM. */
internal enum class FileKind { FOLDER, VIDEO, AUDIO, IMAGE, TEXT, ARCHIVE, OTHER }

internal object ArchiveIcons {
    private val VIDEO = setOf("mkv", "mp4", "avi", "mov", "webm", "m4v", "ts", "m2ts", "wmv", "flv", "mpg", "mpeg", "3gp")
    private val AUDIO = setOf("mp3", "flac", "aac", "m4a", "ogg", "opus", "wav", "wma", "mka")
    private val IMAGE = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
    private val TEXT = setOf("txt", "srt", "ass", "ssa", "vtt", "sub", "log", "md", "json", "xml", "csv", "nfo", "ini", "conf", "sh", "py", "kt", "html")
    private val ARCHIVE = setOf("zip", "7z", "tar", "gz", "bz2", "xz", "tgz", "rar")

    fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    fun kindOf(name: String, isDirectory: Boolean): FileKind {
        if (isDirectory) return FileKind.FOLDER
        val ext = extensionOf(name)
        return when {
            ext in VIDEO -> FileKind.VIDEO
            ext in AUDIO -> FileKind.AUDIO
            ext in IMAGE -> FileKind.IMAGE
            ext in TEXT -> FileKind.TEXT
            ext in ARCHIVE -> FileKind.ARCHIVE
            else -> FileKind.OTHER
        }
    }

    /** Glyph teks (bukan emoji berwarna; U+FE0E memaksa gaya teks) agar tetap abu-putih dan tidak butuh drawable. */
    fun glyph(kind: FileKind): String = when (kind) {
        FileKind.FOLDER -> "\u25B8"
        FileKind.VIDEO -> "\u25B6\uFE0E"
        FileKind.AUDIO -> "\u266A"
        FileKind.IMAGE -> "\u25A3"
        FileKind.TEXT -> "\u2261"
        FileKind.ARCHIVE -> "\u25A4"
        FileKind.OTHER -> "\u00B7"
    }

    fun glyph(n: TreeNode): String = glyph(kindOf(n.name, n.isDirectory))
}

/** Pratinjau gambar sederhana: pemilihan inSampleSize (murni JVM; decode ada di ArchiveSheet). */
internal object ImagePreview {
    /** Sisi terpanjang hasil decode tidak melebihi nilai ini. */
    const val MAX_EDGE = 2048

    fun isImage(name: String): Boolean = ArchiveIcons.kindOf(name, false) == FileKind.IMAGE

    /** Pangkat dua terkecil sehingga lebar dan tinggi hasil <= [maxEdge]. Ukuran tidak valid = 1. */
    fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
        var s = 1
        while (width / s > maxEdge || height / s > maxEdge) s *= 2
        return s
    }
}
