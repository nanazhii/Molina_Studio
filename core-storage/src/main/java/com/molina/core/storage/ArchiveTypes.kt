// molina-patch26k
package com.molina.core.storage

import com.molina.core.common.ErrorKind
import java.io.IOException

/**
 * Format arsip yang dikenali Penjelajah Molina. Level dibatasi demi RAM Android: xz dan 7z (LZMA2)
 * maksimal 6 (kamus 8 MB); preset 7-9 butuh ratusan MB sehingga tidak ditawarkan.
 */
enum class ArchiveFormat(
    val label: String,
    val ext: String,
    val minLevel: Int,
    val maxLevel: Int,
    val defaultLevel: Int,
) {
    ZIP("ZIP", ".zip", 0, 9, 6),
    SEVEN_Z("7Z", ".7z", 0, 6, 6),
    TAR("TAR", ".tar", 0, 0, 0),
    TAR_GZ("TAR.GZ", ".tar.gz", 1, 9, 6),
    TAR_BZ2("TAR.BZ2", ".tar.bz2", 1, 9, 9),
    TAR_XZ("TAR.XZ", ".tar.xz", 0, 6, 6),
    GZ("GZ", ".gz", 0, 0, 0),
    BZ2("BZ2", ".bz2", 0, 0, 0),
    XZ("XZ", ".xz", 0, 0, 0);

    val isTar: Boolean get() = this == TAR || this == TAR_GZ || this == TAR_BZ2 || this == TAR_XZ

    /** gz/bz2/xz tanpa tar: satu berkas terkompresi. Hanya bisa diekstrak, tidak dibuat. */
    val isSingle: Boolean get() = this == GZ || this == BZ2 || this == XZ

    val canCompress: Boolean get() = !isSingle

    /** Dibuat dengan kata sandi (AES-256): ZIP lewat zip4j, 7z lewat `7z` di Debian ([SevenZipBackend], molina-27q). */
    val canEncrypt: Boolean get() = this == ZIP || this == SEVEN_Z

    /** 7z bersandi bisa ikut mengenkripsi nama berkas (`-mhe=on`). */
    val encryptsNames: Boolean get() = this == SEVEN_Z

    val hasLevel: Boolean get() = maxLevel > minLevel

    fun clampLevel(level: Int): Int = level.coerceIn(minLevel, maxLevel)
}

/** Tujuan ekstrak: langsung ke folder tujuan, atau ke subfolder bernama sama dengan arsip. */
enum class ExtractTarget { HERE, NAMED_FOLDER }

enum class ArchiveKind { EXTRACT, COMPRESS }

/** Alasan gagal yang bisa dibedakan UI. Pesan [ArchiveException] sengaja tanpa nama/path. */
enum class ArchiveFailure {
    NEED_PASSWORD,
    WRONG_PASSWORD,
    UNSUPPORTED,
    CORRUPT,
    NO_SPACE,
    TOO_MANY_ENTRIES,
    UNSAFE_ENTRY,
    NAME_TOO_LONG,
    INVALID_NAME,
    NAME_CONFLICT,
    OUT_OF_MEMORY,
    NO_MATCH,
    EMPTY_SOURCE,
    NOT_FOUND,
    PASSWORD_UNSUPPORTED,
    TOOL_MISSING, // molina-27q: 7z bersandi butuh 7zip di Debian
    WEAK_PASSWORD, // molina-27q
    IO,
    TOO_LARGE, // 26k5b:e03
}

/** Galat operasi arsip; dilampirkan sebagai cause di MolinaError sehingga UI bisa membaca [reason]. */
class ArchiveException(
    val reason: ArchiveFailure,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

internal fun ArchiveFailure.toErrorKind(): ErrorKind = when (this) {
    ArchiveFailure.NO_SPACE -> ErrorKind.NO_SPACE
    ArchiveFailure.UNSAFE_ENTRY -> ErrorKind.INVALID_PATH
    ArchiveFailure.NAME_TOO_LONG, ArchiveFailure.INVALID_NAME, ArchiveFailure.NAME_CONFLICT -> ErrorKind.INVALID_NAME
    ArchiveFailure.NOT_FOUND -> ErrorKind.NOT_FOUND
    else -> ErrorKind.IO
}

data class ArchiveEntryInfo(val name: String, val size: Long, val isDirectory: Boolean)

/**
 * Isi arsip. [entries] dipotong di [ArchiveEngineLimits.LIST_MAX] ([truncated]); jumlah dan total ukuran
 * tetap menghitung semua entri yang terbaca. [encrypted]: ada isi yang butuh kata sandi.
 */
data class ArchiveListing(
    val format: ArchiveFormat,
    val entries: List<ArchiveEntryInfo>,
    val fileCount: Int,
    val folderCount: Int,
    val totalBytes: Long,
    val encrypted: Boolean,
    val truncated: Boolean,
)

/**
 * Hasil membuka satu entri arsip. [relative]: berkas di cache (relatif root), kosong bila [cancelled].
 * [cached] true bila dipakai ulang tanpa ekstrak. // 26k5b:e05
 */
data class OpenedEntry(
    val relative: String,
    val name: String,
    val bytes: Long,
    val cached: Boolean,
    val cancelled: Boolean = false,
)

/** [destRelative]: folder hasil (relatif terhadap root); kosong bila [cancelled]. [skipped]: tautan/berkas khusus tar. */
data class ExtractResult(
    val destRelative: String,
    val files: Int,
    val renamed: Int,
    val skipped: Int,
    val cancelled: Boolean,
    /** Total byte yang ditulis ke disk. molina-patch26k2a */
    val bytes: Long = 0L,
    /** Berkas lama yang diganti (ConflictPolicy.OVERWRITE). 26k4:e01 */
    val replaced: Int = 0,
)

/** [outputRelative]: arsip hasil (relatif terhadap root); kosong bila [cancelled]. */
data class CompressResult(
    val outputRelative: String,
    val entries: Int,
    val skipped: Int,
    val cancelled: Boolean,
    /** Ukuran arsip hasil (byte). molina-patch26k2b */
    val bytes: Long = 0L,
)

/** [baseName] tanpa ekstensi (mentah dari pengguna; divalidasi). [password] untuk ZIP dan 7z; [encryptNames] hanya 7z bersandi. */
data class CompressOptions(
    val format: ArchiveFormat,
    val level: Int,
    val baseName: String,
    val password: String? = null,
    val encryptNames: Boolean = false, // molina-27q
)

data class ArchiveProgress(val kind: ArchiveKind, val bytesDone: Long, val bytesTotal: Long) {
    /** 0f..1f; 0f bila total belum diketahui. */
    val fraction: Float
        get() = if (bytesTotal > 0) (bytesDone.toDouble() / bytesTotal).coerceIn(0.0, 1.0).toFloat() else 0f
}

object ArchiveEngineLimits {
    /** Entri yang ditampilkan di daftar isi (naik dari 5000; yang terpotong ditandai truncated). 26k5a:e01 */
    const val LIST_MAX = 50_000

    /** Batas jumlah entri saat ekstrak (anti bom entri). */
    const val MAX_ENTRIES = 100_000

    /** Batas satu entri yang dibuka ke cache (26k-5b). 26k5b:e04 */
    const val ENTRY_MAX_BYTES = 150L * 1024 * 1024

    /** Batas total isi cache entri arsip; dipangkas LRU. */
    const val CACHE_MAX_BYTES = 300L * 1024 * 1024
}
