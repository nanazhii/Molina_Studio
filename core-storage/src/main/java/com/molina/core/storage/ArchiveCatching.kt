// molina-patch26k
package com.molina.core.storage

import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaError
import com.molina.core.common.MolinaResult
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Seperti molinaCatching, tetapi [ArchiveException] dipetakan ke ErrorKind yang sesuai dan tetap
 * dilampirkan sebagai cause, sehingga UI bisa membaca [ArchiveException.reason] (mis. NEED_PASSWORD).
 */
internal inline fun <T> archiveCatching(block: () -> T): MolinaResult<T> = try {
    MolinaResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: ArchiveException) {
    MolinaResult.Failure(MolinaError(e.reason.toErrorKind(), e.message ?: "Operasi arsip gagal", e))
} catch (e: SecurityException) {
    MolinaResult.Failure(MolinaError(ErrorKind.PERMISSION_DENIED, "Akses ditolak", e))
} catch (e: FileNotFoundException) {
    MolinaResult.Failure(MolinaError(ErrorKind.NOT_FOUND, "Tidak ditemukan", e))
} catch (e: IOException) {
    MolinaResult.Failure(MolinaError(ErrorKind.IO, "Kesalahan I/O", e))
} catch (e: Exception) {
    MolinaResult.Failure(MolinaError(ErrorKind.UNKNOWN, "Kesalahan tak terduga", e))
}
