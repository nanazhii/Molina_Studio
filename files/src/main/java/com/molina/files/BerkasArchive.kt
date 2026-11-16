// molina-27f
package com.molina.files

import android.content.ActivityNotFoundException
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaError
import com.molina.core.storage.ArchiveFailure
import com.molina.core.storage.ArchiveNames
import com.molina.core.storage.ConflictPolicy
import com.molina.core.storage.ExtractTarget
import com.molina.core.ui.MolinaDialog
import com.molina.filemanager.ArchiveAction
import java.io.File

/**
 * [molina-27f] Arsip untuk tab Berkas: Kompres, Ekstrak (di sini / ke folder / subtitle / ke... / banyak arsip), Lihat isi, Uji.
 * Seluruh mesin (FilesArchive, ArchiveDialogs, ArchiveSheet, ArchiveText) memakai yang sudah ada dari 26k; kelas ini hanya
 * menyambungkan menu :filemanager ke sana dan menampung alur dialog (sandi, opsi, hasil) yang di FilesFragment tinggal di Fragment.
 */
internal class BerkasArchive(
    private val fragment: Fragment,
    private val root: File,
    private val onClipChanged: () -> Unit,
    private val onRefresh: () -> Unit,
    private val onOpen: (File) -> Unit, // molina-27g0
) {
    private var passwordPrompting = false

    val clipCount: Int get() = pending?.paths?.size ?: 0

    fun clearClip() {
        val c = pending ?: return
        FilesEvents.archiveClip("clear", c.paths.size)
        pending = null
        onClipChanged()
    }

    /** Dari [ArchiveSheet]: "Ekstrak ke..." untuk satu arsip (opsional hanya entri [only]). */
    fun setSheetClip(archive: String, only: Set<String>?): Boolean {
        if (only != null && only.size > ArchiveBrowse.MAX_SAVED_SELECTION) return false
        pending = ArchiveClip(listOf(archive), only)
        FilesEvents.archiveClip("set", 1)
        onClipChanged()
        return true
    }

    fun run(action: ArchiveAction, files: List<File>, dir: File): Boolean {
        val needsIdle = action != ArchiveAction.VIEW && action != ArchiveAction.EXTRACT_TO
        if (needsIdle && (FilesArchive.isRunning || FilesTransfer.isRunning)) return false
        val rels = files.mapNotNull { BerkasPaths.relative(root, it) }
        if (rels.isEmpty() || rels.size != files.size) return false
        val destRel = BerkasPaths.destRelative(root, dir) ?: return false
        val first = rels.first()
        val parent = BrowserPath.parent(first)
        when (action) {
            ArchiveAction.COMPRESS -> promptCompress(rels, files, destRel)
            ArchiveAction.EXTRACT_HERE ->
                if (rels.size == 1) start(ArchiveRequest(ArchiveOp.EXTRACT, rels, parent, ExtractTarget.HERE))
                else promptExtractOptions(rels, destRel, null, false)
            ArchiveAction.EXTRACT_FOLDER ->
                start(ArchiveRequest(ArchiveOp.EXTRACT, listOf(first), parent, ExtractTarget.NAMED_FOLDER))
            ArchiveAction.EXTRACT_SUBS ->
                start(ArchiveRequest(ArchiveOp.EXTRACT_SUBS, listOf(first), parent, ExtractTarget.HERE, flatten = true))
            ArchiveAction.EXTRACT_TO -> {
                pending = ArchiveClip(rels)
                FilesEvents.archiveClip("set", rels.size)
                onClipChanged()
            }
            ArchiveAction.VIEW -> openSheet(first)
            ArchiveAction.TEST -> start(ArchiveRequest(ArchiveOp.TEST, listOf(first), parent))
        }
        return true
    }

    /** Ekstrak arsip yang menunggu ke [dir] (dialog opsi dulu). */
    fun extractClipInto(dir: File): Boolean {
        val c = pending ?: return false
        val dest = BerkasPaths.destRelative(root, dir) ?: return false
        if (FilesArchive.isRunning || FilesTransfer.isRunning) return false
        promptExtractOptions(c.paths, dest, c.only, true)
        return true
    }

    // --- dialog ---

    private fun promptCompress(rels: List<String>, files: List<File>, destRel: String) {
        val ctx = fragment.context ?: return
        val single = files.singleOrNull()
        val base = ArchiveNames.defaultBaseName(
            singleName = single?.name,
            singleIsDir = single?.isDirectory == true,
            parentName = destRel.substringAfterLast('/'),
        )
        ArchiveDialogs.compress(ctx, base) { format, level, name, password, encryptNames -> // molina-27q
            start(ArchiveRequest(ArchiveOp.COMPRESS, rels, destRel, format = format, level = level, baseName = name, encryptNames = encryptNames), password)
        }
    }

    private fun promptExtractOptions(paths: List<String>, dest: String, only: Set<String>?, fromClip: Boolean) {
        val ctx = fragment.context ?: return
        ArchiveDialogs.extractOptions(ctx, paths.size) { target, policy ->
            val request = ArchiveRequest(ArchiveOp.EXTRACT, paths, dest, target, policy = policy, only = only)
            if (FilesArchive.start(request)) {
                if (fromClip) {
                    pending = null
                    onClipChanged()
                }
            } else {
                notifyBusy()
            }
        }
    }

    private fun openSheet(path: String) {
        val fm = fragment.childFragmentManager
        if (fm.findFragmentByTag(SHEET_TAG) != null) return
        ArchiveSheet.newInstance(path).show(fm, SHEET_TAG)
    }

    // --- hasil dan sandi (pola FilesFragment 26k4) ---

    /** [molina-27g0] Folder hasil Ekstrak (tidak dibatalkan, ada berkas) untuk aksi "Buka"; null bila tidak ada. */
    private fun openTarget(state: ArchiveState.Finished): File? {
        val r = state.extract ?: return null
        if (state.request.op != ArchiveOp.EXTRACT || r.cancelled || r.files == 0) return null // molina-27s: destRelative kosong = root Internal, tetap bisa dibuka
        return File(root, r.destRelative)
    }

    fun onFinished(state: ArchiveState.Finished) {
        val request = state.request
        val reason = state.error?.let { ArchiveText.reasonOf(it) }
        val needsPassword = reason == ArchiveFailure.NEED_PASSWORD || reason == ArchiveFailure.WRONG_PASSWORD
        if (needsPassword && request.op != ArchiveOp.COMPRESS) {
            promptArchivePassword(request, wrong = reason == ArchiveFailure.WRONG_PASSWORD)
            return
        }
        val ctx = fragment.context
        val test = state.test
        if (test != null && !test.cancelled && ctx != null) {
            MolinaDialog.info(ctx, ArchiveText.TEST_TITLE, ArchiveText.testSummary(test), "Tutup")
        } else {
            val opened = openTarget(state) // molina-27g0
            fragment.view?.let { v ->
                val ex = state.extract // molina-27s: sebut lokasi hasil ekstrak
                val lokasi = if (ex != null && !ex.cancelled && ex.files > 0 && request.op != ArchiveOp.TEST) "\nHasil di " + BerkasPaths.lokasi(ex.destRelative) else ""
                Snackbar.make(v, ArchiveText.finished(state) + lokasi, if (lokasi.isNotEmpty()) 8000 else Snackbar.LENGTH_LONG).also { sb ->
                    if (opened != null) sb.setAction("Buka") { onOpen(opened) }
                }.show()
            }
        }
        if (request.op != ArchiveOp.TEST) onRefresh()
        promptNextPassword()
    }

    /** Arsip bersandi dari ekstrak banyak arsip: minta kata sandi satu per satu; Batal = lewati arsip itu. */
    fun promptNextPassword() {
        if (passwordPrompting || FilesArchive.isRunning) return
        val next = FilesArchive.passwordQueue.firstOrNull() ?: return
        promptArchivePassword(next, wrong = false, queued = true)
    }

    private fun promptArchivePassword(request: ArchiveRequest, wrong: Boolean, queued: Boolean = false) {
        val ctx = fragment.context ?: return
        passwordPrompting = true
        val name = request.sources.firstOrNull()?.substringAfterLast('/').orEmpty()
        val others = if (queued) (FilesArchive.passwordQueue.size - 1).coerceAtLeast(0) else 0
        val message = buildString {
            append(if (wrong) "Coba lagi dengan kata sandi yang benar." else "Masukkan kata sandi untuk membuka arsip ini.")
            if (name.isNotEmpty()) append("\n").append(name)
            if (others > 0) append("\nMasih ada ").append(others).append(" arsip bersandi lain.")
        }
        fun dequeue() {
            if (queued) FilesArchive.passwordQueue.removeFirstOrNull()
        }
        ArchiveDialogs.password(
            ctx,
            title = if (wrong) "Kata sandi salah" else "Arsip bersandi",
            message = message,
            onCancel = {
                passwordPrompting = false
                dequeue()
                promptNextPassword()
            },
            onSubmit = { pw ->
                passwordPrompting = false
                dequeue()
                start(request, pw)
            },
        )
    }

    // --- dipakai ArchiveSheet lewat BerkasFragment ---

    fun viewEntry(relative: String): String? {
        val ctx = fragment.context ?: return null
        val file = File(root, relative)
        return try {
            fragment.startActivity(FilesShare.viewIntent(ctx, file))
            null
        } catch (e: ActivityNotFoundException) {
            FilesEvents.failed("archive-open", MolinaError(ErrorKind.IO, "Tidak ada aplikasi untuk membuka berkas", e), relative)
            "Tidak ada aplikasi untuk membuka berkas ini"
        } catch (e: IllegalArgumentException) {
            FilesEvents.failed("archive-open", MolinaError(ErrorKind.INVALID_PATH, "Berkas di luar jalur FileProvider", e), relative)
            "Berkas ini tidak bisa dibuka"
        }
    }

    fun startSheetExtract(
        archive: String,
        only: Set<String>?,
        target: ExtractTarget,
        policy: ConflictPolicy,
        password: String?,
    ): Boolean {
        val request = ArchiveRequest(ArchiveOp.EXTRACT, listOf(archive), BrowserPath.parent(archive), target, policy = policy, only = only)
        return FilesArchive.start(request, password)
    }

    private fun start(request: ArchiveRequest, password: String? = null) {
        if (!FilesArchive.start(request, password)) notifyBusy()
    }

    private fun notifyBusy() {
        fragment.view?.let { Snackbar.make(it, "Operasi lain masih berjalan", Snackbar.LENGTH_SHORT).show() }
    }

    private companion object {
        const val SHEET_TAG = "berkas_archive"

        /** Klip "Ekstrak ke...": tingkat proses agar bertahan antar kunjungan tab (pola sama dengan EmbeddedClipboard). */
        var pending: ArchiveClip? = null
    }
}
