// molina-patch26e1
package com.molina.core.storage

/**
 * Riwayat folder terakhir Penjelajah: maks. [max] (bawaan 5), terbaru di depan.
 * Port ExplorerRecent repo lama; bagian "berkas terakhir untuk nano" tidak dibawa.
 */
class ExplorerRecent(private val store: KeyValueStore, private val max: Int = DEFAULT_MAX) {
    init { require(max >= 1) }

    fun paths(): List<String> = ExplorerPathList.decode(store.getString(KEY))

    /** Folder terakhir yang masih ada menurut [isDirectory], terbaru lebih dulu. */
    fun load(isDirectory: (String) -> Boolean): List<String> = paths().filter(isDirectory)

    /** Folder terakhir yang masih ada; root berupa string kosong, null bila riwayat kosong. */
    fun last(isDirectory: (String) -> Boolean): String? = load(isDirectory).firstOrNull()

    /** true bila dicatat; false untuk path tidak valid. */
    fun push(path: String): Boolean {
        val p = ExplorerPathList.normalize(path) ?: return false
        val list = (listOf(p) + paths().filter { it != p }).take(max)
        store.putString(KEY, ExplorerPathList.encode(list))
        return true
    }

    companion object {
        const val DEFAULT_MAX = 5
        private const val KEY = "recent_v1"
    }
}
