// molina-27m
package com.molina.filemanager

import java.io.File

/**
 * [molina-27m] Ekstensi media yang dibuka default di mpv (Molina Cinema). Murni Kotlin, dapat diuji JVM.
 * Gambar, subtitle, dan daftar putar sengaja tidak termasuk.
 */
internal object MediaExt {
    val VIDEO: Set<String> = setOf(
        "mkv", "mp4", "m4v", "mov", "avi", "webm", "flv", "f4v", "wmv", "asf", "ts", "m2ts", "mts",
        "mpg", "mpeg", "vob", "ogv", "3gp", "3g2", "divx", "rm", "rmvb", "mxf",
    )
    val AUDIO: Set<String> = setOf(
        "mp3", "flac", "ogg", "oga", "opus", "m4a", "aac", "wav", "wma", "ape", "alac", "aiff", "aif",
        "mka", "ac3", "dts", "amr", "mp2", "wv", "tta",
    )

    /** ".ts" juga ekstensi TypeScript: dianggap video hanya bila berkasnya sebesar ini atau lebih. */
    const val TS_MIN_BYTES = 1_048_576L

    /** Ekstensi huruf kecil, atau null bila tidak ada (titik di awal nama bukan ekstensi). */
    fun extOf(name: String): String? {
        val i = name.lastIndexOf('.')
        return if (i > 0 && i < name.length - 1) name.substring(i + 1).lowercase() else null
    }

    fun isPlayableName(name: String): Boolean = extOf(name).let { it != null && (it in VIDEO || it in AUDIO) }

    fun isPlayable(file: File): Boolean {
        if (!isPlayableName(file.name) || file.isDirectory) return false
        return if (extOf(file.name) == "ts") file.length() >= TS_MIN_BYTES else true
    }
}
