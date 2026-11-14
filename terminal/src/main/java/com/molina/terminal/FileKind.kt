// molina-patch34
package com.molina.terminal

/**
 * Jenis berkas menurut ekstensi, dipakai drawer untuk mewarnai dan melambangkan ikon berkas
 * (warna + lambang kecil, agar mudah dibedakan sekilas). Tanpa ekstensi / tidak dikenal = [OTHER].
 */
internal enum class FileKind(val color: Int) {
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

    companion object {
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
