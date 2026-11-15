// molina-27q
package com.molina.core.storage

import com.molina.core.common.SevenZipCommand
import java.io.File
import java.io.IOException

/**
 * [molina-27q] Satu pekerjaan `7z a` bersandi. [sources] = berkas/folder tingkat atas (tanpa tautan simbolik), [output] = berkas
 * keluaran di dalam [stageDir] (folder kerja milik mesin, kosong), [excludes] = jalur dalam arsip yang dilewati.
 */
class SevenZipJob(
    val sources: List<File>,
    val output: File,
    val stageDir: File,
    val level: Int,
    val password: String,
    val encryptNames: Boolean,
    val excludes: List<String>,
)

/** Kegagalan proses 7z; pesan = petunjuk umum (tanpa nama berkas dan tanpa sandi). */
class SevenZipFailure(val failure: SevenZipCommand.Failure, cause: Throwable? = null) : IOException(failure.hint, cause)

/** Penulis 7z bersandi. Diimplementasikan :app (7z di Debian PRoot); penulis commons-compress tidak bisa mengenkripsi 7z. */
interface SevenZipWriter {
    /** null = siap; selain itu alasan singkat (Debian belum terpasang, 7zip belum ada). Blocking ringan. */
    fun unavailableReason(): String?

    /** Blocking. Melapor persen 0..100 lewat [onPercent]; [cancelled] dicek berkala. Gagal = [SevenZipFailure]. */
    fun create(job: SevenZipJob, cancelled: () -> Boolean, onPercent: (Int) -> Unit)
}

/** Titik sambung global (dipasang :app saat start); null = 7z bersandi tidak tersedia, mesin menolak (fail-closed). */
object SevenZipBackend {
    @Volatile
    var writer: SevenZipWriter? = null
}
