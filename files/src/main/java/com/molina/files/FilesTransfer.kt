// molina-patch26c
package com.molina.files

import com.molina.core.common.MolinaError
import com.molina.core.common.MolinaResult
import com.molina.core.storage.BatchResult
import com.molina.core.storage.CancelToken
import com.molina.core.storage.LocalMolinaStorage
import com.molina.core.storage.MolinaStorage
import com.molina.core.storage.TransferKind
import com.molina.core.storage.TransferProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Keadaan operasi massal yang dibaca Fragment. [Finished] sekali pakai: ditutup lewat [FilesTransfer.acknowledge]. */
internal sealed interface TransferState {
    object Idle : TransferState

    data class Running(
        val kind: TransferKind,
        val progress: TransferProgress?,
        val cancelling: Boolean,
    ) : TransferState

    data class Finished(
        val id: Long,
        val kind: TransferKind,
        val result: BatchResult?,
        val error: MolinaError?,
    ) : TransferState
}

/**
 * Menjalankan salin/pindah/hapus massal di luar siklus hidup Fragment, sehingga memutar layar atau
 * berpindah tab tidak menghentikan operasi. Tidak memegang Context/View. Satu operasi pada satu waktu.
 */
internal object FilesTransfer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val storage: MolinaStorage = LocalMolinaStorage()
    private val mutableState = MutableStateFlow<TransferState>(TransferState.Idle)
    private var token: CancelToken? = null
    private var seq = 0L

    val state: StateFlow<TransferState> get() = mutableState

    val isRunning: Boolean get() = mutableState.value is TransferState.Running

    /** Memulai operasi. false bila operasi lain masih berjalan atau [sources] kosong. Panggil dari main thread. */
    fun start(kind: TransferKind, sources: List<String>, destPath: String): Boolean {
        if (isRunning || sources.isEmpty()) return false
        val cancel = CancelToken()
        token = cancel
        mutableState.value = TransferState.Running(kind, null, false)
        FilesEvents.transferStart(kind, sources.size, destPath)
        val onProgress: (TransferProgress) -> Unit = { p ->
            mutableState.update { if (it is TransferState.Running) it.copy(progress = p) else it }
        }
        scope.launch {
            val result = when (kind) {
                TransferKind.COPY -> storage.copy(sources, destPath, cancel, onProgress)
                TransferKind.MOVE -> storage.move(sources, destPath, cancel, onProgress)
                TransferKind.DELETE -> storage.deleteAll(sources, cancel, onProgress)
            }
            finish(kind, result, destPath)
        }
        return true
    }

    /** Meminta pembatalan; hasil sebagian tetap dilaporkan lewat [TransferState.Finished]. */
    fun cancel() {
        val running = mutableState.value as? TransferState.Running ?: return
        token?.cancel()
        mutableState.value = running.copy(cancelling = true)
    }

    /** Menutup keadaan Finished yang sudah ditampilkan. */
    fun acknowledge(id: Long) {
        val s = mutableState.value
        if (s is TransferState.Finished && s.id == id) mutableState.value = TransferState.Idle
    }

    private fun finish(kind: TransferKind, result: MolinaResult<BatchResult>, destPath: String) {
        token = null
        val id = ++seq
        when (result) {
            is MolinaResult.Success -> {
                val r = result.value
                FilesEvents.transferDone(r, destPath)
                r.firstError?.let { FilesEvents.transferFailed(kind, it, destPath) }
                mutableState.value = TransferState.Finished(id, kind, r, null)
            }
            is MolinaResult.Failure -> {
                FilesEvents.transferFailed(kind, result.error, destPath)
                mutableState.value = TransferState.Finished(id, kind, null, result.error)
            }
        }
    }
}
