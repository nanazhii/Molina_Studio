package com.molina.files

import com.molina.core.common.ErrorKind

/** Teks galat untuk pengguna. Sengaja tanpa path (lihat docs/penamaan.md). */
object FilesErrorText {
    fun of(kind: ErrorKind): String = when (kind) {
        ErrorKind.NOT_FOUND -> "Folder tidak ditemukan"
        ErrorKind.PERMISSION_DENIED ->
            "Izin penyimpanan belum diberikan. Jika dialog tidak muncul, aktifkan lewat Setelan Android > Aplikasi > Molina > Izin."
        ErrorKind.INVALID_PATH -> "Path tidak valid"
        ErrorKind.INVALID_NAME -> "Nama tidak valid (kosong, memuat \"/\", atau lebih dari 255 byte)"
        ErrorKind.ALREADY_EXISTS -> "Nama sudah dipakai di folder ini"
        ErrorKind.PROTECTED -> "Folder ini dilindungi dan tidak bisa diubah atau dihapus"
        ErrorKind.NO_SPACE -> "Ruang penyimpanan tidak cukup"
        ErrorKind.IO -> "Kesalahan I/O saat membaca folder"
        ErrorKind.UNKNOWN -> "Kesalahan tak terduga"
    }

    /** Teks galat untuk operasi tulis (buat/ganti nama/hapus). */
    fun forOp(kind: ErrorKind): String = when (kind) {
        ErrorKind.NOT_FOUND -> "Item tidak ditemukan"
        ErrorKind.PERMISSION_DENIED -> "Akses ditolak. Periksa izin penyimpanan"
        ErrorKind.IO -> "Operasi gagal (periksa izin tulis)"
        else -> of(kind)
    }

    // molina-patch26c
    /** Teks galat untuk salin/pindah/hapus massal. */
    fun forTransfer(kind: ErrorKind): String = when (kind) {
        ErrorKind.INVALID_PATH -> "Tujuan berada di dalam folder yang disalin atau dipindah"
        ErrorKind.PROTECTED -> "Folder standar Molina dilindungi"
        else -> forOp(kind)
    }
}
