// molina-27d
package com.molina.filemanager

import java.io.File

/**
 * [molina-27d] Titik sambung file manager tertanam ke fitur milik modul lain (Favorit, Sampah).
 * Diimplementasikan oleh :files; Terminal tidak memakainya. Semua [File] absolut; implementasi yang menerjemahkan ke path relatif.
 */
interface FileManagerActions {
    /** True bila [file] di dalam root Molina (Favorit dan Sampah berlaku). */
    fun canManage(file: File): Boolean
    fun isFavorite(dir: File): Boolean
    fun toggleFavorite(dir: File)
    suspend fun favoriteDirs(): List<File>

    /** Pindah ke Sampah; [onResult] dipanggil di main thread (true = berhasil). */
    fun trash(files: List<File>, onResult: (Boolean) -> Unit)
    fun openTrash()

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
