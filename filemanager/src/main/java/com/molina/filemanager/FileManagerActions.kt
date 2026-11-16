// molina-27d
package com.molina.filemanager

import java.io.File

/**
 * [molina-27d] Titik sambung file manager tertanam ke fitur milik modul lain (Favorit, Sampah).
 * Diimplementasikan oleh :files; Terminal tidak memakainya. Semua [File] absolut; implementasi yang menerjemahkan ke path relatif.
 */
/** [molina-27e] Jenis operasi papan klip. Dipetakan implementasi ke jenis transfer miliknya. */
enum class TransferMode { COPY, MOVE }

/** [molina-27f] Aksi arsip yang diminta file manager tertanam; dijalankan oleh implementasi (:files). */
enum class ArchiveAction { COMPRESS, EXTRACT_HERE, EXTRACT_FOLDER, EXTRACT_SUBS, EXTRACT_TO, VIEW, TEST }

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

    /** [molina-27f] True bila [file] berkas arsip yang dikenali (ZIP, 7z, tar, ...). */
    fun isArchive(file: File): Boolean = false

    /**
     * [molina-27f] Menjalankan [action] pada [files]; [dir] = folder tujuan hasil (Kompres, Ekstrak banyak arsip).
     * false bila ditolak (operasi lain berjalan, atau di luar root Molina). Dipanggil dari main thread.
     */
    fun archive(action: ArchiveAction, files: List<File>, dir: File): Boolean = false

    /** [molina-27f] Jumlah arsip yang menunggu folder tujuan ("Ekstrak ke..."); 0 = tidak ada. */
    fun archiveClipCount(): Int = 0

    /** [molina-27f] Ekstrak arsip yang menunggu ke [dir]. false bila ditolak. */
    fun extractClipInto(dir: File): Boolean = false

    fun clearArchiveClip() = Unit

    /**
     * [molina-27g] Dialog Properti (jenis, ukuran, isi, tanggal, akses, SHA-256) untuk [files]; file di root Molina
     * (termasuk root itu sendiri). false bila ditolak; View lalu memakai dialog bawaan.
     */
    fun properties(files: List<File>): Boolean = false

    /** [molina-27g] Bagikan [files] lewat chooser sistem (folder dilewati). false bila ditolak. */
    fun share(files: List<File>): Boolean = false

    /** [molina-27m] Putar [file] media di Molina Cinema (mpv). false bila ditolak; View menampilkan toast. */
    fun play(file: File): Boolean = false

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
