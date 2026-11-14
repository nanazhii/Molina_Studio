package com.molina.core.common

import kotlin.coroutines.cancellation.CancellationException

// molina-patch26a: INVALID_NAME, ALREADY_EXISTS, PROTECTED untuk operasi berkas
enum class ErrorKind { NOT_FOUND, PERMISSION_DENIED, INVALID_PATH, INVALID_NAME, ALREADY_EXISTS, PROTECTED, IO, UNKNOWN, NO_SPACE }

/** Pesan sengaja tanpa path/isi data (lihat docs/penamaan.md: log tidak mencatat path penuh). */
data class MolinaError(
    val kind: ErrorKind,
    val message: String,
    val cause: Throwable? = null,
)

sealed interface MolinaResult<out T> {
    data class Success<T>(val value: T) : MolinaResult<T>
    data class Failure(val error: MolinaError) : MolinaResult<Nothing>
}

inline fun <T, R> MolinaResult<T>.map(transform: (T) -> R): MolinaResult<R> = when (this) {
    is MolinaResult.Success -> MolinaResult.Success(transform(value))
    is MolinaResult.Failure -> this
}

fun <T> MolinaResult<T>.getOrNull(): T? = (this as? MolinaResult.Success)?.value

fun <T> MolinaResult<T>.errorOrNull(): MolinaError? = (this as? MolinaResult.Failure)?.error

/** Membungkus blok yang bisa melempar; pembatalan coroutine selalu diteruskan, tidak ditelan. */
inline fun <T> molinaCatching(block: () -> T): MolinaResult<T> = try {
    MolinaResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: SecurityException) {
    MolinaResult.Failure(MolinaError(ErrorKind.PERMISSION_DENIED, "Akses ditolak", e))
} catch (e: java.io.FileNotFoundException) {
    MolinaResult.Failure(MolinaError(ErrorKind.NOT_FOUND, "Tidak ditemukan", e))
} catch (e: java.io.IOException) {
    MolinaResult.Failure(MolinaError(ErrorKind.IO, "Kesalahan I/O", e))
} catch (e: Exception) {
    MolinaResult.Failure(MolinaError(ErrorKind.UNKNOWN, "Kesalahan tak terduga", e))
}
