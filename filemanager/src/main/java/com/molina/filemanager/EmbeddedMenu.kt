// molina-27e
package com.molina.filemanager

/**
 * [molina-27d2][molina-27e] Satu-satunya sumber isi menu titik tiga mode tertanam. Menu baris (target = baris itu),
 * menu header (target = folder aktif), dan menu mode pilih (target = semua item terpilih) dibangun dari sini,
 * seperti W7ToolsMenu di repo lama. Murni Kotlin tanpa Android agar bisa diuji JVM.
 * Aksi baru ditambah di [Action] + [buildNormal]/[buildSelecting] + penanganan di FileDrawerView.
 */
internal object EmbeddedMenu {
    enum class Action {
        REFRESH, NEW_DIR, NEW_FILE, RENAME,
        SELECT, SELECT_ALL, EXIT_SELECT, COPY, MOVE, PASTE,
        FAVORITE, COPY_PATH, INFO, TRASH, DELETE_PERMANENT, FAVORITES_LIST, OPEN_TRASH,
    }

    data class Entry(val action: Action, val label: String, val enabled: Boolean = true, val danger: Boolean = false)

    /**
     * [targetCount] 0 = menu header pada folder aktif, 1 = satu baris, lebih dari 1 = mode pilih.
     * [global] true untuk menu header (menambah Segarkan, Daftar Favorit, Sampah).
     * [selecting] true untuk menu header saat mode pilih aktif: hanya aksi massal.
     */
    data class State(
        val targetCount: Int,
        val hasActions: Boolean,
        val favoriteAvailable: Boolean,
        val isFavorite: Boolean,
        val trashAvailable: Boolean,
        val global: Boolean,
        val selecting: Boolean = false,
        val transferAvailable: Boolean = false,
        val clipCount: Int = 0,
        val pasteAvailable: Boolean = false,
    ) {
        val hasTarget: Boolean get() = targetCount > 0
    }

    fun build(s: State): List<Entry> = if (s.selecting) buildSelecting(s) else buildNormal(s)

    private fun buildSelecting(s: State): List<Entry> {
        val l = ArrayList<Entry>()
        l += Entry(Action.SELECT_ALL, "Pilih semua")
        l += Entry(Action.COPY, "Salin", enabled = s.hasTarget && s.transferAvailable)
        l += Entry(Action.MOVE, "Pindah", enabled = s.hasTarget && s.transferAvailable)
        if (s.trashAvailable) l += Entry(Action.TRASH, "Hapus", danger = true)
        l += Entry(Action.DELETE_PERMANENT, "Hapus permanen\u2026", enabled = s.hasTarget, danger = true)
        l += Entry(Action.EXIT_SELECT, "Batal pilih")
        return l
    }

    private fun buildNormal(s: State): List<Entry> {
        val l = ArrayList<Entry>()
        if (s.global) l += Entry(Action.REFRESH, "Segarkan")
        l += Entry(Action.NEW_DIR, "Folder baru")
        l += Entry(Action.NEW_FILE, "Berkas baru")
        if (s.hasTarget) l += Entry(Action.RENAME, "Ubah nama")
        if (s.hasActions) {
            l += Entry(Action.SELECT, if (s.hasTarget) "Pilih" else "Mode pilih")
            if (s.hasTarget) {
                l += Entry(Action.COPY, "Salin", enabled = s.transferAvailable)
                l += Entry(Action.MOVE, "Pindah", enabled = s.transferAvailable)
            }
            if (s.clipCount > 0) l += Entry(Action.PASTE, "Tempel di sini (${s.clipCount})", enabled = s.pasteAvailable)
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
