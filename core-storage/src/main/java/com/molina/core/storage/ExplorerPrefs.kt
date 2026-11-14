// molina-patch26e1
package com.molina.core.storage

/** Setelan sortir Penjelajah yang diingat antar pembukaan (port ExplorerPrefs repo lama). */
class ExplorerPrefs(private val store: KeyValueStore) {
    data class Data(
        val sortKey: SortKey = SortKey.NAME,
        val ascending: Boolean = true,
        val foldersFirst: Boolean = true,
        val showHidden: Boolean = false,
        val deep: Boolean = false,
        val playedFirst: Boolean = false,
    ) {
        /** Terapkan ke [base]; kueri dan filter ekstensi milik [base] tidak diubah. */
        fun applyTo(base: ListOptions): ListOptions = base.copy(
            sortKey = sortKey, ascending = ascending, foldersFirst = foldersFirst,
            showHidden = showHidden, deep = deep,
        )

        companion object {
            fun from(o: ListOptions, playedFirst: Boolean = false) = Data(
                o.sortKey, o.ascending, o.foldersFirst, o.showHidden, o.deep, playedFirst,
            )
        }
    }

    fun load(): Data {
        val key = runCatching { SortKey.valueOf(store.getString("sort_key") ?: "NAME") }.getOrDefault(SortKey.NAME)
        return Data(
            sortKey = key,
            ascending = store.getBoolean("sort_asc", true),
            foldersFirst = store.getBoolean("folders_first", true),
            showHidden = store.getBoolean("show_hidden", false),
            deep = store.getBoolean("deep_search", false),
            playedFirst = store.getBoolean("played_first", false),
        )
    }

    fun save(d: Data) {
        store.putString("sort_key", d.sortKey.name)
        store.putBoolean("sort_asc", d.ascending)
        store.putBoolean("folders_first", d.foldersFirst)
        store.putBoolean("show_hidden", d.showHidden)
        store.putBoolean("deep_search", d.deep)
        store.putBoolean("played_first", d.playedFirst)
    }
}
