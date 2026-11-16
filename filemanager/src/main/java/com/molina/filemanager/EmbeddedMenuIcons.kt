// molina-27l
package com.molina.filemanager

import com.molina.filemanager.EmbeddedMenu.Action

/**
 * [molina-27l] Pemetaan murni aksi menu ke ikon dan label ringkas. Tanpa Android agar bisa diuji JVM.
 * `when` di [glyph] sengaja tanpa else: aksi baru di [EmbeddedMenu.Action] wajib diberi ikon, kalau tidak build gagal.
 */
internal object EmbeddedMenuIcons {
    enum class Glyph {
        REFRESH, NEW_FOLDER, NEW_FILE, EDIT, CHECK, SELECT_ALL, CLOSE, COPY, MOVE, PASTE, ARCHIVE, UNARCHIVE, SUBTITLES, VIEW, SHARE, STAR, LINK, INFO, TRASH, DELETE_FOREVER, SEARCH, SORT, EYE, EYE_OFF, PHONE, TERMINAL, DOWNLOAD, PHOTO, DOCS, PLAY;

        /** Nama berkas vektor di res/drawable (tanpa .xml). */
        val file: String get() = "ic_mnu_" + name.lowercase()
    }

    fun glyph(e: EmbeddedMenu.Entry): Glyph = when (e.action) {
        Action.REFRESH -> Glyph.REFRESH
        Action.NEW_DIR -> Glyph.NEW_FOLDER
        Action.NEW_FILE -> Glyph.NEW_FILE
        Action.RENAME -> Glyph.EDIT
        Action.SELECT, Action.TEST_ARCHIVE -> Glyph.CHECK
        Action.SELECT_ALL -> Glyph.SELECT_ALL
        Action.EXIT_SELECT -> Glyph.CLOSE
        Action.COPY -> Glyph.COPY
        Action.MOVE -> Glyph.MOVE
        Action.PASTE -> Glyph.PASTE
        Action.COMPRESS -> Glyph.ARCHIVE
        Action.EXTRACT_HERE, Action.EXTRACT_FOLDER, Action.EXTRACT_TO, Action.EXTRACT_PASTE -> Glyph.UNARCHIVE
        Action.EXTRACT_SUBS -> Glyph.SUBTITLES
        Action.VIEW_ARCHIVE -> Glyph.VIEW
        Action.SHARE -> Glyph.SHARE
        Action.FAVORITE, Action.FAVORITES_LIST, Action.GO_MOLINA -> Glyph.STAR
        Action.COPY_PATH -> Glyph.LINK
        Action.INFO -> Glyph.INFO
        Action.TRASH, Action.OPEN_TRASH -> Glyph.TRASH
        Action.DELETE_PERMANENT -> Glyph.DELETE_FOREVER
        Action.GO_INTERNAL -> Glyph.PHONE
        Action.GO_DEBIAN -> Glyph.TERMINAL
        Action.TOGGLE_HIDDEN -> if (e.label.startsWith("Sembunyikan")) Glyph.EYE_OFF else Glyph.EYE
        Action.SORT -> Glyph.SORT
        Action.SEARCH -> Glyph.SEARCH
        Action.GO_DOWNLOAD -> Glyph.DOWNLOAD
        Action.GO_DCIM -> Glyph.PHOTO
        Action.GO_DOCUMENTS -> Glyph.DOCS
        Action.PLAY -> Glyph.PLAY // molina-27m
    }

    /** Kelompok lokasi (bekas awalan ☰): ditaruh di bawah garis pemisah dan diwarnai aksen. */
    fun isNav(a: Action): Boolean = when (a) {
        Action.GO_MOLINA, Action.GO_DOWNLOAD, Action.GO_DCIM, Action.GO_DOCUMENTS,
        Action.FAVORITES_LIST, Action.GO_INTERNAL, Action.GO_DEBIAN, Action.OPEN_TRASH,
        -> true
        else -> false
    }

    /** Label untuk tampilan: awalan ☰ dan ★ dibuang (diganti ikon) dan "file tersembunyi" dipersingkat. */
    fun label(e: EmbeddedMenu.Entry): String =
        e.label.removePrefix("\u2630").trim().removePrefix("\u2605").trim().replace(" file tersembunyi", " tersembunyi")
}
