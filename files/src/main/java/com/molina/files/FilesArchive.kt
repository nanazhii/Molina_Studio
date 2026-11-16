// molina-patch26k2
package com.molina.files

import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaError
import com.molina.core.common.MolinaResult
import com.molina.core.storage.ArchiveBatchResult // 26k4:e19
import com.molina.core.storage.ArchiveException
import com.molina.core.storage.ArchiveTestResult
import com.molina.core.storage.ConflictPolicy
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
internal enum class ArchiveOp { EXTRACT, EXTRACT_SUBS, COMPRESS, TEST } // 26k4:e20

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
    /** Kebijakan bila berkas hasil sudah ada. 26k4:e22 */
    val policy: ConflictPolicy = ConflictPolicy.RENAME,
    /** Hanya entri ini (kunci ArchiveTree: berkas "a/b", folder "a/b/"); null = semua. 26k5c:e01 */
    val only: Set<String>? = null,
    /** 7z bersandi: nama berkas ikut terenkripsi (-mhe=on). molina-27q */
    val encryptNames: Boolean = false,
) {
    /** extract | extract-subs | compress (untuk log). */
    val opName: String
        get() = when (op) {
            ArchiveOp.EXTRACT -> "extract"
            ArchiveOp.EXTRACT_SUBS -> "extract-subs"
            ArchiveOp.COMPRESS -> "compress"
            ArchiveOp.TEST -> "test" // 26k4:e21
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
        /** Ekstrak banyak arsip: arsip ke-[index] (0-based) dari [count]. 26k4:e23 */
        val index: Int = 0,
        val count: Int = 1,
    ) : ArchiveState

    data class Finished(
        val id: Long,
        val request: ArchiveRequest,
        val extract: ExtractResult?,
        val compress: CompressResult?,
        val error: MolinaError?,
        /** Hasil ekstrak banyak arsip / Uji arsip. 26k4:e24 */
        val batch: ArchiveBatchResult? = null,
        val test: ArchiveTestResult? = null,
    ) : ArchiveState
}

/**
 * Menjalankan ekstrak/kompres di luar siklus hidup Fragment (memutar layar tidak menghentikan operasi).
 * Pola sama dengan [FilesTransfer]: satu operasi pada satu waktu untuk gabungan salin/pindah/hapus dan arsip.
 */
internal object FilesArchive {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var storage: MolinaStorage = LocalMolinaStorage()

    /** [molina-27j] Ganti root kerja (tab Berkas = Internal). Diabaikan saat operasi berjalan. */
    fun useRoot(paths: com.molina.core.common.MolinaPaths) {
        if (isRunning || FilesTransfer.isRunning) return
        storage = LocalMolinaStorage(paths)
    }
    private val mutableState = MutableStateFlow<ArchiveState>(ArchiveState.Idle)
    private var token: CancelToken? = null
    private var seq = 0L

    val state: StateFlow<ArchiveState> get() = mutableState

    /**
     * Arsip bersandi dari ekstrak banyak arsip yang menunggu kata sandi (tanpa kata sandi; hanya permintaan).
     * Di singleton agar bertahan saat rotasi. Hanya diakses dari main thread. 26k4:e25
     */
    val passwordQueue: ArrayDeque<ArchiveRequest> = ArrayDeque()

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
        FilesEvents.archiveStart(
            request.opName, request.sources.size, request.formatLabel, pw != null, request.destPath,
            policy = ArchiveOptions.policyName(request.policy), // 26k4:e26
        )
        val onProgress: (ArchiveProgress) -> Unit = { p ->
            mutableState.update { if (it is ArchiveState.Running) it.copy(progress = p) else it }
        }
        scope.launch { run(request, pw, cancel, onProgress) }
        return true
    }

    private var sweptAtStart = false // 26k5c:e03

    /**
     * Kosongkan cache entri arsip (Molina/.arsip-cache). [why] = start (sekali per proses) | exit. Dilewati bila ada operasi
     * berjalan. Kegagalan hanya dicatat; cache pasti terpangkas LRU pada pembukaan berikutnya.
     */
    fun sweepCache(why: String) {
        if (why == "start") {
            if (sweptAtStart) return
            sweptAtStart = true
        }
        if (isRunning || FilesTransfer.isRunning) return
        scope.launch {
            when (val r = storage.clearArchiveCache()) {
                is MolinaResult.Success -> FilesEvents.archiveCache(why, r.value)
                is MolinaResult.Failure -> FilesEvents.failed("archive-cache", r.error, "")
            }
        }
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
                val options = CompressOptions(req.format ?: ArchiveFormat.ZIP, req.level, req.baseName, pw, req.encryptNames)
                finishCompress(req, storage.compressItems(req.sources, req.destPath, options, cancel, onProgress))
            }
            ArchiveOp.EXTRACT -> { // 26k4:e27
                if (req.sources.size > 1) {
                    val onItem: (Int, Int) -> Unit = { i, n ->
                        mutableState.update { if (it is ArchiveState.Running) it.copy(index = i, count = n) else it }
                    }
                    when (val b = storage.extractArchives(req.sources, req.destPath, req.target, req.policy, cancel, onItem, onProgress)) {
                        is MolinaResult.Failure -> finish(req, null, null, b.error)
                        is MolinaResult.Success -> finish(req, null, null, null, batch = b.value)
                    }
                } else {
                    val r = storage.extractArchive(req.sources.first(), req.destPath, req.target, pw, req.only, cancel, onProgress, false, req.policy) // 26k5c:e02
                    finishExtract(req, r)
                }
            }
            ArchiveOp.TEST -> when (val t = storage.testArchive(req.sources.first(), pw, cancel, onProgress)) {
                is MolinaResult.Failure -> finish(req, null, null, t.error)
                is MolinaResult.Success -> finish(req, null, null, null, test = t.value)
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

    private fun finish(
        req: ArchiveRequest,
        extract: ExtractResult?,
        compress: CompressResult?,
        error: MolinaError?,
        batch: ArchiveBatchResult? = null, // 26k4:e28
        test: ArchiveTestResult? = null,
    ) {
        token = null
        batch?.let { b ->
            for (rel in b.needPassword) {
                passwordQueue.addLast(ArchiveRequest(ArchiveOp.EXTRACT, listOf(rel), req.destPath, req.target, policy = req.policy))
            }
        }
        val id = ++seq
        when {
            batch != null -> { // 26k4:e29
                for (item in batch.items) item.error?.let { FilesEvents.archiveFailed(req.opName, it, req.destPath) }
                FilesEvents.archiveBatch(
                    batch.total, batch.succeeded, batch.failed, batch.needPassword.size, batch.cancelled,
                    batch.files, batch.renamed, batch.skipped, batch.replaced, batch.bytes, req.destPath,
                )
            }
            test != null -> FilesEvents.archiveTested(
                req.formatLabel, test.ok, test.entries, test.bytes, test.failure?.name ?: "none",
                test.badIndex, test.checksummed, test.cancelled, req.destPath,
            )
            extract != null -> FilesEvents.archiveDone(
                req.opName, extract.files, extract.renamed, extract.skipped, extract.cancelled, extract.bytes, req.destPath,
                replaced = extract.replaced, // 26k4:e32
            )
            compress != null -> FilesEvents.archiveDone(
                req.opName, compress.entries, 0, compress.skipped, compress.cancelled, compress.bytes, req.destPath,
            )
            error != null -> FilesEvents.archiveFailed(req.opName, error, req.destPath)
        }
        mutableState.value = ArchiveState.Finished(id, req, extract, compress, error, batch, test) // 26k4:e30
    }
}
