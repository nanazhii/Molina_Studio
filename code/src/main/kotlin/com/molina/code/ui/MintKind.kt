package com.molina.code.ui

/**
 * [MINT-TREE] Jenis berkas untuk ikon dan warna nama di panel direktori Code.
 * Salinan murni-Kotlin dari FileKind (:core-ui) supaya tampilannya sama dengan drawer Terminal;
 * :code tidak bergantung pada :core-ui (module_gate). Bila FileKind berubah, ubah berkas ini juga.
 */
internal enum class MintKind(val argb: Int) {
    SHELL(0xFF00E676.toInt()),
    CODE(0xFF448AFF.toInt()),
    MARKUP(0xFFFF9800.toInt()),
    DATA(0xFFFFCA28.toInt()),
    IMAGE(0xFFFF4081.toInt()),
    VIDEO(0xFFFF5252.toInt()),
    AUDIO(0xFFB388FF.toInt()),
    SUBTITLE(0xFF00BFA5.toInt()),
    ARCHIVE(0xFFFFAB40.toInt()),
    TEXT(0xFFB0BEC5.toInt()),
    OTHER(0xFFFFFFFF.toInt());

    /** Warna teks nama: putih yang sedikit condong ke warna jenisnya (sama dengan FileKind.nameColor). */
    val nameArgb: Int = mixArgb(0xFFFFFFFF.toInt(), argb, NAME_TINT)

    companion object {
        private val BY_EXT: Map<String, MintKind> = buildMap {
            fun add(kind: MintKind, vararg exts: String) = exts.forEach { put(it, kind) }
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
        fun of(name: String): MintKind {
            val dot = name.lastIndexOf('.')
            if (dot <= 0 || dot == name.length - 1) return OTHER
            return BY_EXT[name.substring(dot + 1).lowercase()] ?: OTHER
        }
    }
}

private const val NAME_TINT = 0.28f

private fun mixArgb(base: Int, tint: Int, t: Float): Int {
    fun ch(shift: Int): Int {
        val a = (base shr shift) and 0xFF
        val b = (tint shr shift) and 0xFF
        return (a + (b - a) * t).toInt().coerceIn(0, 255)
    }
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}
