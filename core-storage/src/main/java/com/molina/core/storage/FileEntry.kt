package com.molina.core.storage

/**
 * Satu entri folder. [relDir] hanya terisi pada hasil pencarian sampai subfolder: folder induk entri
 * relatif terhadap folder yang dicari (null = langsung di folder itu).
 */
data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    val relDir: String? = null,
) {
    val isHidden: Boolean get() = name.startsWith(".")
    val extension: String get() = name.substringAfterLast('.', "")
}
