package com.molina.cinema

import java.io.File

/** Satu baris di pemilih berkas. */
data class BrowseEntry(val file: File, val isDir: Boolean) {
    val name: String get() = file.name
}

/** Logika murni (tanpa Android) untuk pemilih berkas Beranda Cinema; bisa diuji JVM. */
object CinemaBrowse {
    val MEDIA_EXT: Set<String> = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "m4v", "ts", "m2ts", "flv", "wmv", "3gp", "mpg", "mpeg",
        "mp3", "flac", "m4a", "ogg", "opus", "wav", "aac", "wma",
    )

    fun isMedia(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in MEDIA_EXT

    /** Folder dan berkas media di [dir]; folder dulu, lalu urut nama tanpa membedakan huruf besar/kecil. Berkas tersembunyi dilewati. */
    fun list(dir: File): List<BrowseEntry> {
        val all = dir.listFiles() ?: return emptyList()
        return all
            .filter { !it.name.startsWith(".") && (it.isDirectory || isMedia(it.name)) }
            .map { BrowseEntry(it, it.isDirectory) }
            .sortedWith(compareByDescending<BrowseEntry> { it.isDir }.thenBy { it.name.lowercase() })
    }

    /** Awal penelusuran: <root>/Molina/cinema bila ada, selain itu <root>. */
    fun startDir(root: File): File {
        val c = File(root, "Molina/cinema")
        return if (c.isDirectory) c else root
    }

    /** Folder induk yang masih di dalam [root]; null bila [dir] sudah di [root] (atau di luarnya). */
    fun parentWithin(dir: File, root: File): File? {
        val r = root.canonicalPath
        val d = dir.canonicalPath
        if (d == r) return null
        val p = dir.parentFile ?: return null
        val pc = p.canonicalPath
        return if (pc == r || pc.startsWith("$r/")) p else null
    }
}

/** Daftar "Terakhir diputar": hanya path berkas lokal (tautan tidak disimpan karena bisa memuat token). */
object CinemaRecent {
    const val MAX = 10

    fun push(list: List<String>, path: String, max: Int = MAX): List<String> =
        (listOf(path) + list.filter { it != path }).take(max)

    fun encode(list: List<String>): String = list.joinToString("\n")

    fun decode(s: String?): List<String> = s.orEmpty().split('\n').filter { it.isNotBlank() }
}
