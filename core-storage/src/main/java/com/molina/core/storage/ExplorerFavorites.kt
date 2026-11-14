// molina-patch26e1
package com.molina.core.storage

/**
 * Favorit buatan pengguna untuk panel kiri Penjelajah (port ExplorerFavorites repo lama).
 * Menyimpan path relatif; root tidak bisa difavoritkan karena sudah selalu tampil.
 */
class ExplorerFavorites(private val store: KeyValueStore) {
    fun paths(): List<String> = ExplorerPathList.decode(store.getString(KEY))

    /** Favorit yang foldernya masih ada menurut [isDirectory]. */
    fun load(isDirectory: (String) -> Boolean): List<String> = paths().filter(isDirectory)

    fun contains(path: String): Boolean {
        val p = ExplorerPathList.normalize(path) ?: return false
        return p in paths()
    }

    /** true bila ditambahkan; false untuk root, path tidak valid, atau sudah ada. */
    fun add(path: String): Boolean {
        val p = ExplorerPathList.normalize(path)
        if (p.isNullOrEmpty()) return false
        val list = paths()
        if (p in list) return false
        store.putString(KEY, ExplorerPathList.encode(list + p))
        return true
    }

    /** true bila ada yang dihapus. */
    fun remove(path: String): Boolean {
        val p = ExplorerPathList.normalize(path) ?: return false
        val list = paths()
        if (p !in list) return false
        store.putString(KEY, ExplorerPathList.encode(list - p))
        return true
    }

    private companion object { const val KEY = "favorites_v1" }
}
