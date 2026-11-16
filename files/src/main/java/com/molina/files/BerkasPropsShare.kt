// molina-27g
package com.molina.files

import android.content.ActivityNotFoundException
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaError
import com.molina.core.common.MolinaResult
import com.molina.core.storage.FileProps
import com.molina.core.storage.MolinaStorage
import com.molina.core.ui.MolinaDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * [molina-27g] Penghubung Properti dan Bagikan untuk tab Berkas. Seluruh logika (PropsText, FilesShare, storage.properties,
 * storage.shareTargets, storage.sha256, FilesEvents) sudah ada di :files; kelas ini hanya tempat dialog/intent dipasang
 * pada Fragment Berkas. Mengembalikan false bila permintaan ditolak (di luar root Molina atau tampilan belum ada).
 */
internal class BerkasPropsShare(
    private val host: Fragment,
    private val storage: MolinaStorage,
    private val root: File,
) {
    private var propsJob: Job? = null

    /** Folder aktif boleh jadi root Molina itu sendiri (path relatif kosong), jadi memakai destRelative. */
    fun properties(files: List<File>): Boolean {
        val rels = files.mapNotNull { BerkasPaths.destRelative(root, it) }
        if (rels.isEmpty() || rels.size != files.size) return false
        if (host.view == null) return false
        propsJob?.cancel()
        propsJob = host.viewLifecycleOwner.lifecycleScope.launch {
            when (val r = storage.properties(rels)) {
                is MolinaResult.Failure -> {
                    FilesEvents.failed("props", r.error, rels.first())
                    snack(FilesErrorText.forOp(r.error.kind))
                }
                is MolinaResult.Success -> present(r.value)
            }
        }
        return true
    }

    fun share(files: List<File>): Boolean {
        val rels = files.mapNotNull { BerkasPaths.relative(root, it) }
        if (rels.isEmpty() || rels.size != files.size) return false
        if (host.view == null) return false
        host.viewLifecycleOwner.lifecycleScope.launch {
            when (val r = storage.shareTargets(rels)) {
                is MolinaResult.Failure -> {
                    FilesEvents.failed("share", r.error, rels.first())
                    snack(FilesErrorText.forOp(r.error.kind))
                }
                is MolinaResult.Success -> {
                    val targets = r.value
                    if (targets.files.isEmpty()) {
                        snack("Folder tidak bisa dibagikan. Pilih berkas")
                        return@launch
                    }
                    try {
                        host.startActivity(FilesShare.chooser(host.requireContext(), targets.files))
                        FilesEvents.share(targets.files.size, targets.skipped)
                        if (targets.skipped > 0) snack("${targets.skipped} item dilewati (folder)")
                    } catch (e: ActivityNotFoundException) {
                        FilesEvents.failed("share", MolinaError(ErrorKind.IO, "Tidak ada aplikasi untuk berbagi", e), rels.first())
                        snack("Tidak ada aplikasi untuk berbagi")
                    } catch (e: IllegalArgumentException) {
                        FilesEvents.failed("share", MolinaError(ErrorKind.INVALID_PATH, "Berkas di luar jalur FileProvider", e), rels.first())
                        snack("Berkas ini tidak bisa dibagikan")
                    }
                }
            }
        }
        return true
    }

    private fun present(items: List<FileProps>) {
        if (items.isEmpty()) return
        val scope = if (items.size > 1) "multi" else if (items[0].isDirectory) "folder" else "file"
        FilesEvents.props(scope, items.size, items[0].path)
        val hashTarget = items.singleOrNull()?.takeIf { !it.isDirectory && !it.isSymlink }
        val view = PropsText.View("Internal", root.absolutePath) // molina-27u: root Berkas = penyimpanan Internal
        val body = PropsText.body(items, view = view)
        var hashJob: Job? = null
        MolinaDialog.info(
            context = host.requireContext(),
            title = PropsText.title(items, "Internal"),
            message = body,
            positive = "Tutup",
            secondary = if (hashTarget != null) "SHA-256" else null,
            onSecondary = { handle ->
                if (hashTarget != null && hashJob?.isActive != true) {
                    handle.setMessage(body + "\n\nSHA-256: menghitung…")
                    hashJob = host.viewLifecycleOwner.lifecycleScope.launch { hash(hashTarget, body, handle) }
                }
            },
            onDismiss = { hashJob?.cancel() },
        )
    }

    private suspend fun hash(target: FileProps, body: String, handle: MolinaDialog.Info) {
        var lastPct = -1
        try {
            val r = storage.sha256(target.path) { done, total ->
                val pct = if (total > 0) (done * 100 / total).toInt() else 0
                if (pct != lastPct) {
                    lastPct = pct
                    handle.setMessage(body + "\n\nSHA-256: menghitung… " + pct + "%")
                }
            }
            when (r) {
                is MolinaResult.Success -> {
                    FilesEvents.propsHash("ok")
                    handle.setMessage(body + "\n\nSHA-256:\n" + r.value)
                }
                is MolinaResult.Failure -> {
                    FilesEvents.failed("hash", r.error, target.path)
                    handle.setMessage(body + "\n\nSHA-256 gagal: " + FilesErrorText.forOp(r.error.kind))
                }
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            FilesEvents.propsHash("cancelled")
            throw e
        }
    }

    private fun snack(text: String) {
        host.view?.let { Snackbar.make(it, text, Snackbar.LENGTH_LONG).show() }
    }
}
