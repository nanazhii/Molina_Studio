// molina-patch26k2
package com.molina.files

import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaError
import com.molina.core.common.MolinaResult
import com.molina.core.storage.ArchiveException
import com.molina.core.storage.ArchiveFailure
import com.molina.core.storage.ArchiveFormat
import com.molina.core.storage.ArchiveNames
import com.molina.core.storage.ArchiveProgress
import com.molina.core.storage.CancelToken
import com.molina.core.storage.CompressOptions
import com.molina.core.storage.CompressResult
import com.molina.core.storage.ExtractResult
import com.molina.core.storage.ExtractTarget
import com.molina.core.storage.LocalMolinaStorage
import com.molina.core.storage.MolinaStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** EXTRACT_SUBS: hanya berkas subtitle dari arsip, langsung ke folder arsip (satu ketukan dari menu). */
internal enum class ArchiveOp { EXTRACT, EXTRACT_SUBS, COMPRESS }

/**
 * Permintaan operasi arsip. Sengaja TIDAK memuat kata sandi: kata sandi hanya dilewatkan ke
 * [FilesArchive.start] dan tidak pernah masuk state, log, atau Bundle.
 * Ekstrak: [sources] = satu arsip, [destPath] = folder tujuan. Kompres: [sources] = item, [destPath] = folder hasil.
 */
internal data class ArchiveRequest(
    val op: ArchiveOp,
    val sources: List<String>,
    val destPath: String,
    val target: ExtractTarget = ExtractTarget.HERE,
    val format: ArchiveFormat? = null,
    val level: Int = 0,
    val baseName: String = "",
    /** Ekstrak rata: semua berkas ke [destPath] tanpa subfolder (dipakai Ekstrak subtitle). */
    val flatten: Boolean = false, // 26k3:request
) {
    /** extract | extract-subs | compress (untuk log). */
    val opName: String
        get() = when (op) {
            ArchiveOp.EXTRACT -> "extract"
            ArchiveOp.EXTRACT_SUBS -> "extract-subs"
            ArchiveOp.COMPRESS -> "compress"
        }

    /** Label format huruf kecil untuk log (zip, 7z, tar.gz, ...); tanpa nama berkas. */
    val formatLabel: String
        get() {
            val f = format ?: ArchiveNames.detect(sources.firstOrNull().orEmpty().substringAfterLast('/'))
            return f?.label?.lowercase(Locale.ROOT) ?: "unknown"
        }
}

/** Keadaan operasi arsip yang dibaca Fragment. [Finished] sekali pakai: ditutup lewat [FilesArchive.acknowledge]. */
internal sealed interface ArchiveState {
    object Idle : ArchiveState

    data class Running(
        val op: ArchiveOp,
        val progress: ArchiveProgress?,
        val cancelling: Boolean,
    ) : ArchiveState

    data class Finished(
        val id: Long,
        val request: ArchiveRequest,
        val extract: ExtractResult?,
        val compress: CompressResult?,
        val error: MolinaError?,
    ) : ArchiveState
}

/**
 * Menjalankan ekstrak/kompres di luar siklus hidup Fragment (memutar layar tidak menghentikan operasi).
 * Pola sama dengan [FilesTransfer]: satu operasi pada satu waktu untuk gabungan salin/pindah/hapus dan arsip.
 */
internal object FilesArchive {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val storage: MolinaStorage = LocalMolinaStorage()
    private val mutableState = MutableStateFlow<ArchiveState>(ArchiveState.Idle)
    private var token: CancelToken? = null
    private var seq = 0L

    val state: StateFlow<ArchiveState> get() = mutableState

    val isRunning: Boolean get() = mutableState.value is ArchiveState.Running

    /**
     * Memulai operasi. false bila operasi lain berjalan atau [ArchiveRequest.sources] kosong. Panggil dari main thread.
     * [password]: ZIP/7z saat ekstrak, atau ZIP saat kompres (AES-256); null/kosong = tanpa sandi.
     */
    fun start(request: ArchiveRequest, password: String? = null): Boolean {
        if (isRunning || FilesTransfer.isRunning || request.sources.isEmpty()) return false
        val cancel = CancelToken()
        token = cancel
        val pw = password?.takeIf { it.isNotEmpty() }
        mutableState.value = ArchiveState.Running(request.op, null, false)
        FilesEvents.archiveStart(request.opName, request.sources.size, request.formatLabel, pw != null, request.destPath)
        val onProgress: (ArchiveProgress) -> Unit = { p ->
            mutableState.update { if (it is ArchiveState.Running) it.copy(progress = p) else it }
        }
        scope.launch { run(request, pw, cancel, onProgress) }
        return true
    }

    /** Meminta pembatalan; operasi menghapus hasil sebagian lalu melaporkan [ArchiveState.Finished]. */
    fun cancel() {
        val running = mutableState.value as? ArchiveState.Running ?: return
        token?.cancel()
        mutableState.value = running.copy(cancelling = true)
    }

    fun acknowledge(id: Long) {
        val s = mutableState.value
        if (s is ArchiveState.Finished && s.id == id) mutableState.value = ArchiveState.Idle
    }

    private suspend fun run(req: ArchiveRequest, pw: String?, cancel: CancelToken, onProgress: (ArchiveProgress) -> Unit) {
        when (req.op) {
            ArchiveOp.COMPRESS -> {
                val options = CompressOptions(req.format ?: ArchiveFormat.ZIP, req.level, req.baseName, pw)
                finishCompress(req, storage.compressItems(req.sources, req.destPath, options, cancel, onProgress))
            }
            ArchiveOp.EXTRACT -> {
                val r = storage.extractArchive(req.sources.first(), req.destPath, req.target, pw, null, cancel, onProgress)
                finishExtract(req, r)
            }
            ArchiveOp.EXTRACT_SUBS -> {
                val archive = req.sources.first()
                when (val listed = storage.listArchive(archive, pw)) {
                    is MolinaResult.Failure -> finish(req, null, null, listed.error)
                    is MolinaResult.Success -> {
                        val names = ArchiveSubs.pick(listed.value.entries)
                        if (names.isEmpty()) {
                            val none = ArchiveException(ArchiveFailure.NO_MATCH, "Tidak ada subtitle di arsip")
                            finish(req, null, null, MolinaError(ErrorKind.IO, none.message.orEmpty(), none))
                        } else {
                            val r = storage.extractArchive(archive, req.destPath, ExtractTarget.HERE, pw, names, cancel, onProgress, req.flatten) // 26k3:subs
                            finishExtract(req, r)
                        }
                    }
                }
            }
        }
    }

    private fun finishExtract(req: ArchiveRequest, r: MolinaResult<ExtractResult>) = when (r) {
        is MolinaResult.Success -> finish(req, r.value, null, null)
        is MolinaResult.Failure -> finish(req, null, null, r.error)
    }

    private fun finishCompress(req: ArchiveRequest, r: MolinaResult<CompressResult>) = when (r) {
        is MolinaResult.Success -> finish(req, null, r.value, null)
        is MolinaResult.Failure -> finish(req, null, null, r.error)
    }

    private fun finish(req: ArchiveRequest, extract: ExtractResult?, compress: CompressResult?, error: MolinaError?) {
        token = null
        val id = ++seq
        when {
            extract != null -> FilesEvents.archiveDone(
                req.opName, extract.files, extract.renamed, extract.skipped, extract.cancelled, extract.bytes, req.destPath,
            )
            compress != null -> FilesEvents.archiveDone(
                req.opName, compress.entries, 0, compress.skipped, compress.cancelled, compress.bytes, req.destPath,
            )
            error != null -> FilesEvents.archiveFailed(req.opName, error, req.destPath)
        }
        mutableState.value = ArchiveState.Finished(id, req, extract, compress, error)
    }
}
