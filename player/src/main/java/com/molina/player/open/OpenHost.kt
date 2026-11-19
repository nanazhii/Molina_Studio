package com.molina.player.open

import android.content.Context
import java.io.File

/** Kontrak paket `open` ke layar: hanya yang perlu untuk memutar hasil pilihan dan mengingat pilihan. */
interface OpenHost {
    fun hostContext(): Context

    /** Pemilih baru dibuka (layar menyimpan pilihan "Ingat pilihan"). */
    fun onPickerOpened()

    /** Putar satu berkas dari [path]. */
    fun playPath(path: String)

    /** Putar isi [dir] sebagai daftar putar; [deep] = subfolder ikut. */
    fun playFolder(dir: File, deep: Boolean)
}
