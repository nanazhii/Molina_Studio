// molina-27d2
package com.molina.filemanager

/**
 * [molina-27d2] Satu-satunya sumber isi menu titik tiga mode tertanam. Menu baris (target = baris itu)
 * dan menu header (target = folder aktif) dibangun dari sini, seperti W7ToolsMenu di repo lama.
 * Murni Kotlin tanpa Android agar bisa diuji JVM. Aksi baru ditambah di [Action] + [build] + penanganan di FileDrawerView.
 */
internal object EmbeddedMenu {
    enum class Action {
        REFRESH, NEW_DIR, NEW_FILE, RENAME, FAVORITE, COPY_PATH, INFO,
        TRASH, DELETE_PERMANENT, FAVORITES_LIST, OPEN_TRASH,
    }

    data class Entry(val action: Action, val label: String, val enabled: Boolean = true, val danger: Boolean = false)

    /** [global] true untuk menu header (menambah Segarkan, Daftar Favorit, Sampah). */
    data class State(
        val hasTarget: Boolean,
        val hasActions: Boolean,
        val favoriteAvailable: Boolean,
        val isFavorite: Boolean,
        val trashAvailable: Boolean,
        val global: Boolean,
    )

    fun build(s: State): List<Entry> {
        val l = ArrayList<Entry>()
        if (s.global) l += Entry(Action.REFRESH, "Segarkan")
        l += Entry(Action.NEW_DIR, "Folder baru")
        l += Entry(Action.NEW_FILE, "Berkas baru")
        if (s.hasTarget) l += Entry(Action.RENAME, "Ubah nama")
        if (s.hasActions) {
            l += Entry(Action.FAVORITE, if (s.isFavorite) "Hapus dari Favorit" else "Tambah ke Favorit", enabled = s.favoriteAvailable)
        }
        l += Entry(Action.COPY_PATH, "Salin path")
        l += Entry(Action.INFO, "Properti")
        if (s.hasTarget) {
            if (s.trashAvailable) l += Entry(Action.TRASH, "Hapus", danger = true)
            l += Entry(Action.DELETE_PERMANENT, "Hapus permanen\u2026", danger = true)
        }
        if (s.global && s.hasActions) {
            l += Entry(Action.FAVORITES_LIST, "Daftar Favorit")
            l += Entry(Action.OPEN_TRASH, "Sampah")
        }
        return l
    }
}
