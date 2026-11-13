// molina-patch13c
package com.molina.files

import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaError
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

    fun permissionMissing(path: String, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(AREA, "permission-missing", fields = mapOf("depth" to depth(path).toString()), logger = logger)

    fun permissionRequested(logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(AREA, "permission-request", logger = logger)

    fun permissionResult(granted: Boolean, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(AREA, "permission-result", fields = mapOf("granted" to granted.toString()), logger = logger)

    /** [op] = "list" atau "layout" -> kode files.list-failed / files.layout-failed (BUG). */
    fun failed(op: String, error: MolinaError, path: String, logger: MolinaLogger = MolinaLog.logger) {
        val cause = error.cause
        val fields = linkedMapOf(
            "kind" to error.kind.name,
            "error" to (cause?.javaClass?.simpleName?.ifEmpty { null } ?: "none"),
            "depth" to depth(path).toString(),
        )
        errnoOf(cause)?.let { fields["errno"] = it }
        val level = when (error.kind) {
            ErrorKind.NOT_FOUND, ErrorKind.INVALID_PATH -> LogLevel.WARN
            else -> LogLevel.ERROR
        }
        MolinaEvents.bug(AREA, op + "-failed", error.message, fields = fields, level = level, logger = logger)
    }

    /** Hanya nama errno baku yang diambil dari rantai penyebab; sisa pesan (path) dibuang. */
    fun errnoOf(t: Throwable?): String? =
        generateSequence(t) { it.cause }.take(5).firstNotNullOfOrNull { ERRNO.find(it.message.orEmpty())?.value }
}
