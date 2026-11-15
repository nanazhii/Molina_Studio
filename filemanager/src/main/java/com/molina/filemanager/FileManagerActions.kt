// molina-27d
package com.molina.filemanager

import java.io.File

/**
 * [molina-27d] Titik sambung file manager tertanam ke fitur milik modul lain (Favorit, Sampah).
 * Diimplementasikan oleh :files; Terminal tidak memakainya. Semua [File] absolut; implementasi yang menerjemahkan ke path relatif.
 */
/** [molina-27e] Jenis operasi papan klip. Dipetakan implementasi ke jenis transfer miliknya. */
enum class TransferMode { COPY, MOVE }

interface FileManagerActions {
    /** True bila [file] di dalam root Molina (Favorit dan Sampah berlaku). */
    fun canManage(file: File): Boolean
    fun isFavorite(dir: File): Boolean
    fun toggleFavorite(dir: File)
    suspend fun favoriteDirs(): List<File>

    /** Pindah ke Sampah; [onResult] dipanggil di main thread (true = berhasil). */
    fun trash(files: List<File>, onResult: (Boolean) -> Unit)
    fun openTrash()

    /** [molina-27e] True bila [dir] boleh jadi tujuan Salin/Pindah (di dalam root Molina, bukan Sampah). */
    fun canPasteInto(dir: File): Boolean = false

    /**
     * [molina-27e] Memulai salin/pindah [files] ke [destDir]. false bila operasi lain masih berjalan atau
     * permintaan ditolak. Progres dan hasil dilaporkan implementasi; setelah selesai ia memanggil
     * FileManagerEmbed.Handle.transferFinished. Dipanggil dari main thread.
     */
    fun transfer(mode: TransferMode, files: List<File>, destDir: File): Boolean = false

    /** Tanpa aksi: perilaku bawaan (Hapus permanen, tanpa Favorit/Sampah). */
    object None : FileManagerActions {
        override fun canManage(file: File): Boolean = false
        override fun isFavorite(dir: File): Boolean = false
        override fun toggleFavorite(dir: File) = Unit
        override suspend fun favoriteDirs(): List<File> = emptyList()
        override fun trash(files: List<File>, onResult: (Boolean) -> Unit) = onResult(false)
        override fun openTrash() = Unit
    }
}
