package com.molina.files

import com.molina.core.common.ErrorKind

/** Teks galat untuk pengguna. Sengaja tanpa path (lihat docs/penamaan.md). */
object FilesErrorText {
    fun of(kind: ErrorKind): String = when (kind) {
        ErrorKind.NOT_FOUND -> "Folder tidak ditemukan"
        ErrorKind.PERMISSION_DENIED ->
            "Izin penyimpanan belum diberikan. Jika dialog tidak muncul, aktifkan lewat Setelan Android > Aplikasi > Molina > Izin."
        ErrorKind.INVALID_PATH -> "Path tidak valid"
        ErrorKind.IO -> "Kesalahan I/O saat membaca folder"
        ErrorKind.UNKNOWN -> "Kesalahan tak terduga"
    }
}
