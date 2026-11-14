// molina-patch35
package com.molina.core.ui

import android.graphics.Color

/**
 * Jenis berkas menurut ekstensi + warna bersama (satu sumber untuk drawer Terminal, Berkas, dan Cinema).
 * [color] = warna ikon/lambang; [nameColor] = warna teks nama (putih dicampur sedikit [color], tetap tenang).
 * Tanpa ekstensi / tidak dikenal = [OTHER] (putih polos).
 */
enum class FileKind(val color: Int) {
    SHELL(0xFF00E676.toInt()),    // hijau Molina, lambang ">_"
    CODE(0xFF448AFF.toInt()),     // biru, lambang "<>"
    MARKUP(0xFFFF9800.toInt()),   // oranye, lambang "</>"
    DATA(0xFFFFCA28.toInt()),     // kuning, lambang "[ ]"
    IMAGE(0xFFFF4081.toInt()),    // merah muda, lambang gunung
    VIDEO(0xFFFF5252.toInt()),    // merah, lambang play
    AUDIO(0xFFB388FF.toInt()),    // ungu, lambang not
    SUBTITLE(0xFF00BFA5.toInt()), // hijau kebiruan, lambang dua baris teks
    ARCHIVE(0xFFFFAB40.toInt()),  // jingga, lambang ritsleting
    TEXT(0xFFB0BEC5.toInt()),     // abu-abu, lambang tiga baris teks
    OTHER(0xFFFFFFFF.toInt());    // putih polos (gaya lama)

    /** Warna teks nama berkas: putih yang sedikit condong ke warna jenisnya. */
    val nameColor: Int = mix(Color.WHITE, color, NAME_TINT)

    companion object {
        private const val NAME_TINT = 0.28f

        private val BY_EXT: Map<String, FileKind> = buildMap {
            fun add(kind: FileKind, vararg exts: String) = exts.forEach { put(it, kind) }
            add(SHELL, "sh", "bash", "zsh", "fish")
            add(CODE, "py", "kt", "kts", "java", "js", "mjs", "c", "cc", "cpp", "h", "hpp", "gradle", "lua", "rb", "go", "rs", "php")
            add(MARKUP, "xml", "html", "htm", "css")
            add(DATA, "json", "yml", "yaml", "toml", "ini", "conf", "cfg", "properties", "csv", "tsv", "sql")
            add(IMAGE, "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico", "heic", "avif")
            add(VIDEO, "mp4", "mkv", "avi", "mov", "webm", "m4v", "flv", "wmv", "mpg", "mpeg", "3gp")
            add(AUDIO, "mp3", "flac", "ogg", "opus", "wav", "m4a", "aac", "wma", "mka")
            add(SUBTITLE, "srt", "ass", "ssa", "vtt", "sub")
            add(ARCHIVE, "zip", "tar", "gz", "tgz", "xz", "bz2", "7z", "rar", "zst", "deb", "apk")
            add(TEXT, "txt", "md", "log", "pdf", "rst")
        }

        /** "skrip.tar.gz" -> ARCHIVE (ekstensi terakhir); ".bashrc" / "README" -> OTHER. */
        fun of(name: String): FileKind {
            val dot = name.lastIndexOf('.')
            if (dot <= 0 || dot == name.length - 1) return OTHER
            return BY_EXT[name.substring(dot + 1).lowercase()] ?: OTHER
        }
    }
}

private fun mix(base: Int, tint: Int, t: Float): Int {
    fun ch(shift: Int): Int {
        val a = (base shr shift) and 0xFF
        val b = (tint shr shift) and 0xFF
        return (a + (b - a) * t).toInt().coerceIn(0, 255)
    }
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}
