// molina-patch26c
package com.molina.core.storage

import com.molina.core.common.MolinaError

/**
 * Token pembatalan untuk operasi massal. Berbeda dengan pembatalan coroutine, hasil sebagian
 * (jumlah selesai/gagal) tetap dikembalikan, sehingga UI bisa melaporkan "dibatalkan, N selesai".
 */
class CancelToken {
    @Volatile
    var isCancelled: Boolean = false
        private set

    fun cancel() {
        isCancelled = true
    }
}

enum class TransferKind { COPY, MOVE, DELETE }

/**
 * Kemajuan operasi massal. [itemIndex] berbasis 0 (item tingkat atas yang sedang diproses);
 * [bytesTotal] adalah jumlah ukuran semua berkas hasil pemindaian awal.
 */
data class TransferProgress(
    val kind: TransferKind,
    val itemIndex: Int,
    val itemCount: Int,
    val bytesDone: Long,
    val bytesTotal: Long,
) {
    /** 0f..1f. Bila tidak ada byte (hanya folder kosong), dihitung dari jumlah item. */
    val fraction: Float
        get() = when {
            bytesTotal > 0 -> (bytesDone.toDouble() / bytesTotal).coerceIn(0.0, 1.0).toFloat()
            itemCount > 0 -> (itemIndex.toFloat() / itemCount).coerceIn(0f, 1f)
            else -> 0f
        }
}

/**
 * Ringkasan satu operasi massal. [skipped]: item yang tidak perlu dikerjakan (mis. dipindah ke folder
 * asalnya sendiri). [firstError]: galat item gagal pertama, tanpa nama/path.
 */
data class BatchResult(
    val kind: TransferKind,
    val succeeded: Int,
    val failed: Int,
    val skipped: Int,
    val cancelled: Boolean,
    val firstError: MolinaError?,
)
