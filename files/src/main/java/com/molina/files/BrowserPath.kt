package com.molina.files

/** Navigasi path relatif terhadap root Molina (string kosong = root). Murni, tanpa Android. */
object BrowserPath {
    fun isRoot(path: String): Boolean = path.isEmpty()

    fun parent(path: String): String = path.substringBeforeLast('/', "")

    fun child(path: String, name: String): String = if (path.isEmpty()) name else "$path/$name"

    fun title(path: String): String = if (path.isEmpty()) "Molina" else "Molina/$path"
}
