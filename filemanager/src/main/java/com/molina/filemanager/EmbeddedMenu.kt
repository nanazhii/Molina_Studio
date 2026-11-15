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
        COMPRESS, EXTRACT_HERE, EXTRACT_FOLDER, EXTRACT_SUBS, EXTRACT_TO, EXTRACT_PASTE, VIEW_ARCHIVE, TEST_ARCHIVE, // molina-27f
        SHARE, // molina-27g
        SORT, // molina-27g2
        SEARCH, // molina-27g3
        FAVORITE, COPY_PATH, INFO, TRASH, DELETE_PERMANENT, FAVORITES_LIST, OPEN_TRASH,
        GO_INTERNAL, GO_DEBIAN, // molina-27i: kelompok hamburger di bawah menu titik tiga
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
        val archiveAvailable: Boolean = false, // molina-27f: target di dalam root Molina
        val allArchives: Boolean = false, // molina-27f: semua target berkas arsip
        val archiveClipCount: Int = 0,
        val shareAvailable: Boolean = false, // molina-27g: target di dalam root Molina dan bukan folder tunggal
        val propsAvailable: Boolean = false, // molina-27g: Properti kaya di menu mode pilih
        val sortLabel: String = "", // molina-27g2: label urutan aktif; kosong = tanpa item Urut
        val searchLabel: String = "", // molina-27g3: "Cari\u2026" atau "Tutup pencarian"; kosong = tanpa item Cari
    ) {
        val hasTarget: Boolean get() = targetCount > 0
    }

    fun build(s: State): List<Entry> = if (s.selecting) buildSelecting(s) else buildNormal(s)

    private fun buildSelecting(s: State): List<Entry> {
        val l = ArrayList<Entry>()
        l += Entry(Action.SELECT_ALL, "Pilih semua")
        if (s.targetCount == 1) { // molina-27h: satu item terpilih = isi menu baris lama
            l += Entry(Action.RENAME, "Ubah nama")
            if (s.favoriteAvailable) l += Entry(Action.FAVORITE, if (s.isFavorite) "Hapus dari Favorit" else "Tambah ke Favorit")
            l += Entry(Action.COPY_PATH, "Salin path")
        }
        l += Entry(Action.COPY, "Salin", enabled = s.hasTarget && s.transferAvailable)
        l += Entry(Action.MOVE, "Pindah", enabled = s.hasTarget && s.transferAvailable)
        if (s.shareAvailable) l += Entry(Action.SHARE, "Bagikan", enabled = s.hasTarget) // molina-27g
        archiveEntries(l, s) // molina-27f
        if (s.propsAvailable) l += Entry(Action.INFO, "Properti", enabled = s.hasTarget) // molina-27g
        if (s.trashAvailable) l += Entry(Action.TRASH, "Hapus", danger = true)
        l += Entry(Action.DELETE_PERMANENT, "Hapus permanen\u2026", enabled = s.hasTarget, danger = true)
        l += Entry(Action.EXIT_SELECT, "Batal pilih")
        return l
    }

    /**
     * [molina-27f] Item arsip. Satu arsip: Lihat isi, Ekstrak di sini/ke folder/subtitle/ke..., Uji. Banyak arsip:
     * Ekstrak di sini (dengan dialog opsi) dan Ekstrak ke.... Kompres untuk semua target di dalam root Molina.
     */
    private fun archiveEntries(l: MutableList<Entry>, s: State) {
        if (s.archiveClipCount > 0 && !s.selecting) {
            l += Entry(Action.EXTRACT_PASTE, "Ekstrak di sini (${s.archiveClipCount})", enabled = s.pasteAvailable)
        }
        if (!s.archiveAvailable || !s.hasTarget) return
        l += Entry(Action.COMPRESS, "Kompres\u2026")
        if (!s.allArchives) return
        val single = s.targetCount == 1 // molina-27h: satu arsip terpilih juga dapat Lihat isi/Uji
        if (single) {
            l += Entry(Action.VIEW_ARCHIVE, "Lihat isi")
            l += Entry(Action.EXTRACT_HERE, "Ekstrak di sini")
            l += Entry(Action.EXTRACT_FOLDER, "Ekstrak ke folder")
            l += Entry(Action.EXTRACT_SUBS, "Ekstrak subtitle")
        } else {
            l += Entry(Action.EXTRACT_HERE, "Ekstrak di sini")
        }
        l += Entry(Action.EXTRACT_TO, "Ekstrak ke\u2026")
        if (single) l += Entry(Action.TEST_ARCHIVE, "Uji arsip")
    }

    private fun buildNormal(s: State): List<Entry> {
        val l = ArrayList<Entry>()
        if (s.global) l += Entry(Action.REFRESH, "Segarkan")
        if (s.global && s.sortLabel.isNotEmpty()) l += Entry(Action.SORT, "Urut: ${s.sortLabel}") // molina-27g2
        if (s.global && s.searchLabel.isNotEmpty()) l += Entry(Action.SEARCH, s.searchLabel) // molina-27g3
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
            archiveEntries(l, s) // molina-27f
            if (s.hasTarget && s.shareAvailable) l += Entry(Action.SHARE, "Bagikan") // molina-27g
            l += Entry(Action.FAVORITE, if (s.isFavorite) "Hapus dari Favorit" else "Tambah ke Favorit", enabled = s.favoriteAvailable)
        }
        l += Entry(Action.COPY_PATH, "Salin path")
        l += Entry(Action.INFO, "Properti")
        if (s.hasTarget) {
            if (s.trashAvailable) l += Entry(Action.TRASH, "Hapus", danger = true)
            l += Entry(Action.DELETE_PERMANENT, "Hapus permanen\u2026", danger = true)
        }
        if (s.global) { // molina-27i: kelompok hamburger di bawah (pola DROP-HAMBURGER repo lama): Favorit, Internal, Debian, Sampah
            if (s.hasActions) l += Entry(Action.FAVORITES_LIST, "\u2630 Favorit")
            l += Entry(Action.GO_INTERNAL, "\u2630 Internal")
            l += Entry(Action.GO_DEBIAN, "\u2630 Debian")
            if (s.hasActions) l += Entry(Action.OPEN_TRASH, "\u2630 Sampah")
        }
        return l
    }
}
