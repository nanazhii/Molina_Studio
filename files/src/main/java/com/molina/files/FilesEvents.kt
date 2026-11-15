// molina-patch13c
package com.molina.files

import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaError
import com.molina.core.storage.ArchiveException
import com.molina.core.storage.ArchiveFailure
import com.molina.core.storage.BatchResult // 26k2:events-import
import com.molina.core.storage.TransferKind
import com.molina.log.LogLevel
import com.molina.log.MolinaEvents
import com.molina.log.MolinaLog
import com.molina.log.MolinaLogger

/**
 * Peristiwa terstruktur area `files` (kode otomatis berawalan `files.`). Path TIDAK pernah dicatat:
 * hanya kedalaman folder, jumlah isi, jenis galat, kelas exception, dan kelas errno. Stack trace
 * sengaja tidak dilampirkan karena pesan exception sistem file biasanya memuat path penuh.
 */
internal object FilesEvents {
    private const val AREA = "files"
    private val ERRNO = Regex(
        """\b(EACCES|EPERM|ENOENT|ENOTDIR|EISDIR|EIO|ENOSPC|EROFS|EBUSY|ELOOP|ENAMETOOLONG|EMFILE|ENFILE|ESTALE|ENODEV|ENXIO)\b""",
    )

    /** Jumlah segmen path relatif; root = 0. */
    fun depth(path: String): Int = if (path.isEmpty()) 0 else path.count { it == '/' } + 1

    fun listed(path: String, count: Int, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "list",
            fields = mapOf("depth" to depth(path).toString(), "count" to count.toString()),
            logger = logger,
        )

    // molina-patch26e2
    /** Berkas dibuka di folder terakhir yang diingat (bukan root). Hanya kedalaman, tanpa path. */
    fun restored(path: String, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "restore",
            fields = mapOf("depth" to depth(path).toString()),
            logger = logger,
        )

    // molina-patch26h
    /** Item dipindah ke Sampah. Hanya jumlah, tanpa nama/path. */
    fun trashed(count: Int, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(AREA, "trash", fields = mapOf("count" to count.toString()), logger = logger)

    /** [via]: undo | sheet. */
    fun trashRestored(via: String, count: Int, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "trash-restore",
            fields = mapOf("via" to via, "count" to count.toString()),
            logger = logger,
        )

    /** [via]: sheet | empty | auto. */
    fun trashPurged(via: String, count: Int, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "trash-purge",
            fields = mapOf("via" to via, "count" to count.toString()),
            logger = logger,
        )

    // molina-patch26f
    /** [action]: add | remove. Hanya kedalaman path, tanpa nama folder. */
    fun favorite(action: String, path: String, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "favorite",
            fields = mapOf("action" to action, "depth" to depth(path).toString()),
            logger = logger,
        )

    // molina-patch26g
    /** [action]: sort | select | tools | debian. Tombol pil melayang; tanpa path. */
    fun pill(action: String, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "pill",
            fields = mapOf("action" to action),
            logger = logger,
        )

    // molina-patch26j
    /** [scope]: file | folder | multi. Tanpa nama/path; hanya jenis, jumlah, dan kedalaman. */
    fun props(scope: String, count: Int, path: String, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "props",
            fields = mapOf("scope" to scope, "count" to count.toString(), "depth" to depth(path).toString()),
            logger = logger,
        )

    /** [result]: ok | cancelled. SHA-256 dihitung dari dialog Properti. */
    fun propsHash(result: String, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(AREA, "props-hash", fields = mapOf("result" to result), logger = logger)

    /** [count] berkas dibagikan, [skipped] dilewati (folder/Sampah). Tanpa nama/path. */
    fun share(count: Int, skipped: Int, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "share",
            fields = mapOf("count" to count.toString(), "skipped" to skipped.toString()),
            logger = logger,
        )

    // molina-patch26b
    /** [name]: create-folder | create-file | rename | delete. Tanpa nama/path; hanya jenis dan kedalaman. */
    fun op(name: String, directory: Boolean, path: String, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "op-$name",
            fields = mapOf("kind" to if (directory) "folder" else "file", "depth" to depth(path).toString()),
            logger = logger,
        )

    /** Pencarian nama (kueri TIDAK dicatat, hanya mode dan jumlah hasil). */
    fun searched(path: String, count: Int, deep: Boolean, truncated: Boolean, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "search",
            fields = mapOf(
                "depth" to depth(path).toString(), "deep" to deep.toString(),
                "count" to count.toString(), "truncated" to truncated.toString(),
            ),
            logger = logger,
        )

    // molina-patch26c
    /** [action]: enter | add | remove | all | exit; [via]: swipe | tap | menu | back | op. Tanpa nama/path. */
    fun selection(action: String, via: String, count: Int, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "select",
            fields = mapOf("action" to action, "via" to via, "count" to count.toString()),
            logger = logger,
        )

    /** [action]: set | clear (papan klip internal Salin/Pindah). */
    fun clip(action: String, kind: TransferKind?, count: Int, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "clip",
            fields = linkedMapOf("action" to action, "mode" to (kind?.opName() ?: "none"), "count" to count.toString()),
            logger = logger,
        )

    fun transferStart(kind: TransferKind, count: Int, destPath: String, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "transfer-start",
            fields = mapOf("op" to kind.opName(), "count" to count.toString(), "depth" to depth(destPath).toString()),
            logger = logger,
        )

    fun transferDone(r: BatchResult, destPath: String, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "transfer-done",
            fields = mapOf(
                "op" to r.kind.opName(), "ok" to r.succeeded.toString(), "failed" to r.failed.toString(),
                "skipped" to r.skipped.toString(), "cancelled" to r.cancelled.toString(),
                "depth" to depth(destPath).toString(),
            ),
            logger = logger,
        )

    /** Kode kegagalan memakai [opName]: files.copy-failed | files.move-failed | files.delete-batch-failed. */
    fun transferFailed(kind: TransferKind, error: MolinaError, path: String, logger: MolinaLogger = MolinaLog.logger) =
        failed(kind.opName(), error, path, logger)

    private fun TransferKind.opName(): String = when (this) {
        TransferKind.COPY -> "copy"
        TransferKind.MOVE -> "move"
        TransferKind.DELETE -> "delete-batch"
    }

    // molina-patch26k2 // 26k2:events-fn
    /** [op]: extract | extract-subs | compress. Tanpa nama/path/kata sandi; hanya format, jumlah, dan kedalaman. */
    fun archiveStart(
        op: String,
        count: Int,
        format: String,
        encrypted: Boolean,
        destPath: String,
        logger: MolinaLogger = MolinaLog.logger,
        policy: String = "rename", // 26k4:e33
    ) = MolinaEvents.behavior(
        AREA, "archive-start",
        fields = linkedMapOf(
            "op" to op, "format" to format, "count" to count.toString(),
            "encrypted" to encrypted.toString(), "depth" to depth(destPath).toString(), "policy" to policy, // 26k4:e34
        ),
        logger = logger,
    )

    fun archiveDone(
        op: String,
        ok: Int,
        renamed: Int,
        skipped: Int,
        cancelled: Boolean,
        bytes: Long,
        destPath: String,
        logger: MolinaLogger = MolinaLog.logger,
        replaced: Int = 0, // 26k4:e35
    ) = MolinaEvents.behavior(
        AREA, "archive-done",
        fields = linkedMapOf(
            "op" to op, "ok" to ok.toString(), "renamed" to renamed.toString(), "skipped" to skipped.toString(),
            "cancelled" to cancelled.toString(), "bytes" to bytes.toString(), "depth" to depth(destPath).toString(),
            "replaced" to replaced.toString(), // 26k4:e36
        ),
        logger = logger,
    )

    private val ARCHIVE_WARN = setOf(
        ArchiveFailure.NEED_PASSWORD, ArchiveFailure.WRONG_PASSWORD, ArchiveFailure.UNSUPPORTED,
        ArchiveFailure.NO_MATCH, ArchiveFailure.EMPTY_SOURCE, ArchiveFailure.INVALID_NAME,
        ArchiveFailure.NAME_TOO_LONG, ArchiveFailure.NAME_CONFLICT, ArchiveFailure.NOT_FOUND,
        ArchiveFailure.PASSWORD_UNSUPPORTED, ArchiveFailure.TOO_LARGE, // 26k5b:e21
    )

    /** Kode files.archive-failed; WARN untuk alasan yang bisa diperbaiki pengguna (sandi, nama, format), selain itu ERROR. */
    fun archiveFailed(op: String, error: MolinaError, destPath: String, logger: MolinaLogger = MolinaLog.logger) {
        val cause = error.cause
        val reason = (cause as? ArchiveException)?.reason
        val fields = linkedMapOf(
            "op" to op,
            "kind" to error.kind.name,
            "reason" to (reason?.name ?: "none"),
            "error" to (cause?.javaClass?.simpleName?.ifEmpty { null } ?: "none"),
            "depth" to depth(destPath).toString(),
        )
        errnoOf(cause)?.let { fields["errno"] = it }
        val level = if (reason != null && reason in ARCHIVE_WARN) LogLevel.WARN else LogLevel.ERROR
        MolinaEvents.bug(AREA, "archive-failed", error.message, fields = fields, level = level, logger = logger)
    }

    // 26k4:e37
    /** Ringkasan ekstrak banyak arsip. Tanpa nama/path/kata sandi. */
    fun archiveBatch(
        total: Int,
        ok: Int,
        failed: Int,
        needPassword: Int,
        cancelled: Boolean,
        files: Int,
        renamed: Int,
        skipped: Int,
        replaced: Int,
        bytes: Long,
        destPath: String,
        logger: MolinaLogger = MolinaLog.logger,
    ) = MolinaEvents.behavior(
        AREA, "archive-batch",
        fields = linkedMapOf(
            "total" to total.toString(), "ok" to ok.toString(), "failed" to failed.toString(),
            "needpw" to needPassword.toString(), "cancelled" to cancelled.toString(), "files" to files.toString(),
            "renamed" to renamed.toString(), "skipped" to skipped.toString(), "replaced" to replaced.toString(),
            "bytes" to bytes.toString(), "depth" to depth(destPath).toString(),
        ),
        logger = logger,
    )

    /** Hasil Uji arsip. [badIndex] = indeks entri pertama yang rusak (0-based, -1 = tidak ada/struktur tak terbaca); tanpa nama. */
    fun archiveTested(
        format: String,
        ok: Boolean,
        entries: Int,
        bytes: Long,
        reason: String,
        badIndex: Int,
        checksummed: Boolean,
        cancelled: Boolean,
        destPath: String,
        logger: MolinaLogger = MolinaLog.logger,
    ) = MolinaEvents.behavior(
        AREA, "archive-test",
        fields = linkedMapOf(
            "format" to format, "ok" to ok.toString(), "entries" to entries.toString(), "bytes" to bytes.toString(),
            "bad" to badIndex.toString(), "reason" to reason, "checksummed" to checksummed.toString(),
            "cancelled" to cancelled.toString(), "depth" to depth(destPath).toString(),
        ),
        logger = logger,
    )

    /** Klip "Ekstrak ke…" dipasang/dibatalkan. [action] = set | clear. */
    fun archiveClip(action: String, count: Int, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "archive-clip",
            fields = linkedMapOf("action" to action, "count" to count.toString()),
            logger = logger,
        )

    // 26k5c:e05
    /** Layar Lihat isi dibuka. [skipped] = entri tidak aman yang dilewati pohon. Tanpa nama/path/kata sandi. */
    fun archiveView(
        format: String,
        files: Int,
        folders: Int,
        encrypted: Boolean,
        truncated: Boolean,
        skipped: Int,
        logger: MolinaLogger = MolinaLog.logger,
    ) = MolinaEvents.behavior(
        AREA, "archive-view",
        fields = linkedMapOf(
            "format" to format, "files" to files.toString(), "folders" to folders.toString(),
            "encrypted" to encrypted.toString(), "truncated" to truncated.toString(), "skipped" to skipped.toString(),
        ),
        logger = logger,
    )

    /** Satu entri dibuka lewat cache. [cached] true = slot dipakai ulang tanpa ekstrak. */
    fun archiveOpen(format: String, cached: Boolean, bytes: Long, cancelled: Boolean, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "archive-open",
            fields = linkedMapOf("format" to format, "cached" to cached.toString(), "bytes" to bytes.toString(), "cancelled" to cancelled.toString()),
            logger = logger,
        )

    /** Cache entri arsip dikosongkan. [why] = start | exit | manual. */
    fun archiveCache(why: String, freed: Long, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "archive-cache",
            fields = linkedMapOf("why" to why, "freed" to freed.toString()),
            logger = logger,
        )

    fun permissionMissing(path: String, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(AREA, "permission-missing", fields = mapOf("depth" to depth(path).toString()), logger = logger)

    fun permissionRequested(logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(AREA, "permission-request", logger = logger)

    fun permissionResult(granted: Boolean, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(AREA, "permission-result", fields = mapOf("granted" to granted.toString()), logger = logger)

    /** [op] = list | layout | create-folder | create-file | rename | delete -> kode files.<op>-failed (BUG). */
    fun failed(op: String, error: MolinaError, path: String, logger: MolinaLogger = MolinaLog.logger) {
        val cause = error.cause
        val fields = linkedMapOf(
            "kind" to error.kind.name,
            "error" to (cause?.javaClass?.simpleName?.ifEmpty { null } ?: "none"),
            "depth" to depth(path).toString(),
        )
        errnoOf(cause)?.let { fields["errno"] = it }
        val level = when (error.kind) {
            ErrorKind.NOT_FOUND, ErrorKind.INVALID_PATH, ErrorKind.INVALID_NAME,
            ErrorKind.ALREADY_EXISTS, ErrorKind.PROTECTED -> LogLevel.WARN
            else -> LogLevel.ERROR
        }
        MolinaEvents.bug(AREA, op + "-failed", error.message, fields = fields, level = level, logger = logger)
    }

    /** Hanya nama errno baku yang diambil dari rantai penyebab; sisa pesan (path) dibuang. */
    fun errnoOf(t: Throwable?): String? =
        generateSequence(t) { it.cause }.take(5).firstNotNullOfOrNull { ERRNO.find(it.message.orEmpty())?.value }
}
