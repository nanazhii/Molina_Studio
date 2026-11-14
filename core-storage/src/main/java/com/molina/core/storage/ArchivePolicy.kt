// molina-patch26k4
package com.molina.core.storage

import com.molina.core.common.MolinaError

/**
 * Kebijakan saat berkas hasil ekstrak sudah ada di tujuan.
 * RENAME (bawaan): berkas baru diberi nomor ("a (1).txt"). SKIP: berkas yang ada dibiarkan dan entri dilewati
 * (dihitung ke skipped). OVERWRITE: berkas lama diganti setelah dipindah ke cadangan sementara di folder yang
 * sama; cadangan dihapus hanya bila SELURUH operasi sukses dan dipulihkan bila gagal atau dibatalkan.
 * Folder tidak pernah ditimpa berkas, tautan simbolik tidak pernah ditulisi, dan berkas yang baru ditulis operasi
 * ini sendiri tidak pernah ditimpa (semuanya jatuh ke RENAME).
 */
enum class ConflictPolicy { RENAME, SKIP, OVERWRITE }

/**
 * Hasil Uji arsip. [failure] null = utuh. [badIndex] = indeks (0-based) entri pertama yang rusak, atau -1 bila
 * struktur arsip tidak terbaca. [checksummed] false untuk TAR polos: tidak punya checksum isi, jadi hanya
 * struktur dan ukuran yang bisa diperiksa. Arsip bersandi tanpa/dengan sandi salah TIDAK menjadi hasil:
 * dilempar sebagai ArchiveException (NEED_PASSWORD / WRONG_PASSWORD) agar UI meminta sandi.
 */
data class ArchiveTestResult(
    val entries: Int,
    val bytes: Long,
    val failure: ArchiveFailure?,
    val badIndex: Int,
    val checksummed: Boolean,
    val cancelled: Boolean = false,
) {
    val ok: Boolean get() = failure == null && !cancelled
}

/** Hasil satu arsip dalam ekstrak banyak arsip: tepat satu dari [result] atau [error] terisi. */
data class ArchiveBatchItem(val relative: String, val result: ExtractResult?, val error: MolinaError?) {
    val reason: ArchiveFailure? get() = (error?.cause as? ArchiveException)?.reason
    val needsPassword: Boolean get() = reason == ArchiveFailure.NEED_PASSWORD
}

/**
 * Ringkasan ekstrak banyak arsip. Arsip yang butuh kata sandi TIDAK dihitung ke [failed]; daftarnya ada di
 * [needPassword] agar UI meminta sandi satu per satu. [notRun] = arsip yang belum sempat berjalan (dibatalkan).
 */
data class ArchiveBatchResult(val items: List<ArchiveBatchItem>, val total: Int, val cancelled: Boolean) {
    val succeeded: Int get() = items.count { it.result?.cancelled == false }
    val failed: Int get() = items.count { it.error != null && !it.needsPassword }
    val needPassword: List<String> get() = items.filter { it.needsPassword }.map { it.relative }
    val notRun: Int get() = (total - items.size).coerceAtLeast(0)
    val files: Int get() = items.sumOf { it.result?.files ?: 0 }
    val bytes: Long get() = items.sumOf { it.result?.bytes ?: 0L }
    val renamed: Int get() = items.sumOf { it.result?.renamed ?: 0 }
    val skipped: Int get() = items.sumOf { it.result?.skipped ?: 0 }
    val replaced: Int get() = items.sumOf { it.result?.replaced ?: 0 }
    val firstError: MolinaError? get() = items.firstOrNull { it.error != null && !it.needsPassword }?.error
}
