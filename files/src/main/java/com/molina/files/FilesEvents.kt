// molina-patch13c
package com.molina.files

import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaError
import com.molina.core.storage.BatchResult
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

    // molina-patch26f
    /** [action]: add | remove. Hanya kedalaman path, tanpa nama folder. */
    fun favorite(action: String, path: String, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "favorite",
            fields = mapOf("action" to action, "depth" to depth(path).toString()),
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
