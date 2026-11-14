package com.molina.files

/** Navigasi path relatif terhadap root Molina (string kosong = root). Murni, tanpa Android. */
object BrowserPath {
    fun isRoot(path: String): Boolean = path.isEmpty()

    fun parent(path: String): String = path.substringBeforeLast('/', "")

    fun child(path: String, name: String): String = if (path.isEmpty()) name else "$path/$name"

    fun title(path: String): String = if (path.isEmpty()) "Molina" else "Molina/$path"

    // molina-patch26b
    /** Path relatif sebuah entri; [relDir] terisi pada hasil pencarian sampai subfolder. */
    fun entryPath(current: String, relDir: String?, name: String): String =
        child(if (relDir.isNullOrEmpty()) current else child(current, relDir), name)

    // molina-patch26e-h
    /** Breadcrumb: daftar (label, path); elemen pertama selalu root "Molina". */
    fun crumbs(path: String): List<Pair<String, String>> {
        val out = mutableListOf("Molina" to "")
        var acc = ""
        for (seg in path.split('/').filter { it.isNotEmpty() }) {
            acc = child(acc, seg)
            out += seg to acc
        }
        return out
    }
}
