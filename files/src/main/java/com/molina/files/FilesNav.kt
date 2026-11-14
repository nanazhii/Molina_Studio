// molina-patch26f
package com.molina.files

/**
 * Isi laci kiri Berkas: Favorit (folder standar Molina + favorit pengguna) lalu Penyimpanan
 * (Molina dan Debian). Murni JVM; path relatif terhadap root Molina, root = "".
 */
internal object FilesNav {
    enum class Kind { HEADER, FOLDER, DEBIAN, TRASH }

    data class Item(
        val kind: Kind,
        val label: String,
        val path: String? = null,
        val active: Boolean = false,
        val star: Boolean = false,
        /** true untuk favorit buatan pengguna (tekan lama = hapus). */
        val removable: Boolean = false,
    )

    /** Folder standar Molina yang selalu tampil di Favorit (path relatif, label). */
    val BUILT_IN: List<Pair<String, String>> = listOf(
        "cinema" to "Cinema",
        "terminal" to "Terminal",
        "logs" to "Log",
    )

    fun isBuiltIn(path: String): Boolean = BUILT_IN.any { it.first == path }

    fun build(userFavorites: List<String>, current: String): List<Item> {
        val items = ArrayList<Item>()
        items += Item(Kind.HEADER, "Favorit", star = true)
        BUILT_IN.forEach { (path, label) -> items += Item(Kind.FOLDER, label, path, active = path == current) }
        userFavorites
            .filter { it.isNotEmpty() && !isBuiltIn(it) }
            .forEach { path ->
                items += Item(
                    Kind.FOLDER, path.substringAfterLast('/'), path,
                    active = path == current, removable = true,
                )
            }
        items += Item(Kind.HEADER, "Penyimpanan")
        items += Item(Kind.FOLDER, "Molina", "", active = current.isEmpty())
        items += Item(Kind.DEBIAN, "Debian")
        items += Item(Kind.TRASH, "Sampah")
        return items
    }
}
