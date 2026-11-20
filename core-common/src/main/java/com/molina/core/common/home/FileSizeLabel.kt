package com.molina.core.common.home

import java.util.Locale

/** Ukuran berkas untuk daftar berkas playlist: `412 B`, `7 KB`, `1.2 MB`. */
object FileSizeLabel {
    fun of(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0)
    }
}
