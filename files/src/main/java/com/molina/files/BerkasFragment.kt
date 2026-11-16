// molina-27d
package com.molina.files

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.molina.cinema.PlayRequests // molina-27m
import com.molina.core.common.MolinaPaths
import com.molina.core.common.MolinaResult
import com.molina.core.common.getOrNull
import com.molina.core.storage.ArchiveNames
import com.molina.core.storage.ConflictPolicy
import com.molina.core.storage.ExplorerFavorites
import com.molina.core.storage.ExtractTarget
import com.molina.core.storage.LocalMolinaStorage
import com.molina.core.storage.MolinaStorage
import com.molina.core.storage.PathGuard
import com.molina.core.storage.ScopedKeyValueStore
import com.molina.core.storage.SharedPrefsKeyValueStore
import com.molina.core.storage.TransferKind
import com.molina.core.ui.R as UiR
import com.molina.filemanager.ArchiveAction
import com.molina.filemanager.FileManagerActions
import com.molina.filemanager.FileManagerEmbed
import com.molina.filemanager.TransferMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [molina-27b] Tab Berkas memakai file manager salinan Terminal. FilesFragment lama tetap ada (balik: 1 baris di MainActivity).
 * [molina-27d] Favorit dan Sampah memakai ExplorerFavorites/TrashBin/TrashSheet yang sudah ada; tidak ada logika yang disalin ulang.
 */
class BerkasFragment : Fragment(), ArchiveSheetHost {
    private val paths = MolinaPaths(File(MolinaPaths.INTERNAL_ROOT)) // molina-27j: Berkas = penyimpanan Internal
    private val storage: MolinaStorage by lazy { LocalMolinaStorage(paths) }
    private val explorerStore by lazy { SharedPrefsKeyValueStore(requireContext()) }
    // Favorit memakai path relatif terhadap root, sama dengan FilesFragment, jadi daftarnya dibagi.
    private val favStore by lazy { ExplorerFavorites(ScopedKeyValueStore(explorerStore, paths.root.path)) }
    private var handle: FileManagerEmbed.Handle? = null
    private var strip: TransferStrip? = null // molina-27e
    private var backCallback: OnBackPressedCallback? = null // molina-27e
    private val propsShare by lazy { BerkasPropsShare(this, storage, paths.root) } // molina-27g
    private val archiveOps by lazy { // molina-27f
        BerkasArchive(this, paths.root, { handle?.archiveClipChanged() }, { handle?.refresh() }, { handle?.openDirectory(it) })
    }

    /** [molina-27e] Panel progres salin/pindah di dasar tab (pola sama dengan panel progres FilesFragment). */
    private class TransferStrip(val root: LinearLayout, val text: TextView, val bar: LinearProgressIndicator, val cancel: TextView)

    internal val trashHost: TrashHost = object : TrashHost {
        override val trashStorage: MolinaStorage get() = storage
        override fun onTrashChanged() { handle?.refresh() }
    }

    private val actions = object : FileManagerActions {
        override fun canManage(file: File): Boolean = BerkasPaths.relative(paths.root, file) != null

        override fun isFavorite(dir: File): Boolean =
            BerkasPaths.relative(paths.root, dir)?.let { favStore.contains(it) } == true

        override fun toggleFavorite(dir: File) {
            val rel = BerkasPaths.relative(paths.root, dir) ?: return
            val had = favStore.contains(rel)
            val changed = if (had) favStore.remove(rel) else favStore.add(rel)
            if (!changed) return
            FilesEvents.favorite(if (had) "remove" else "add", rel)
            say(if (had) "Dihapus dari Favorit" else "Ditambahkan ke Favorit")
        }

        override suspend fun favoriteDirs(): List<File> = withContext(Dispatchers.IO) {
            favStore.load { rel -> PathGuard.resolveInside(paths.root, rel).getOrNull()?.isDirectory == true }
                .map { File(paths.root, it) }
        }

        override fun trash(files: List<File>, onResult: (Boolean) -> Unit) {
            val rels = files.mapNotNull { BerkasPaths.relative(paths.root, it) }
            if (rels.isEmpty() || rels.size != files.size) { onResult(false); return }
            viewLifecycleOwner.lifecycleScope.launch {
                when (val r = storage.trash(rels)) {
                    is MolinaResult.Success -> {
                        FilesEvents.trashed(r.value.size)
                        showUndo(r.value.map { it.id })
                        onResult(true)
                    }
                    is MolinaResult.Failure -> {
                        FilesEvents.failed("trash", r.error, "")
                        say(r.error.message)
                        onResult(false)
                    }
                }
            }
        }

        // molina-27e: Salin/Pindah memakai FilesTransfer yang sudah ada (path relatif terhadap root Molina)
        override fun canPasteInto(dir: File): Boolean = BerkasPaths.destRelative(paths.root, dir) != null

        override fun transfer(mode: TransferMode, files: List<File>, destDir: File): Boolean {
            val dest = BerkasPaths.destRelative(paths.root, destDir) ?: return false
            val rels = files.mapNotNull { BerkasPaths.relative(paths.root, it) }
            if (rels.isEmpty() || rels.size != files.size) return false
            val kind = if (mode == TransferMode.MOVE) TransferKind.MOVE else TransferKind.COPY
            return FilesTransfer.start(kind, rels, dest)
        }

        // molina-27f: arsip lewat FilesArchive/ArchiveDialogs/ArchiveSheet yang sudah ada
        override fun isArchive(file: File): Boolean = !file.isDirectory && ArchiveNames.detect(file.name) != null

        override fun play(file: File): Boolean = file.isFile && file.canRead() && PlayRequests.request(file.path) // molina-27m

        override fun archive(action: ArchiveAction, files: List<File>, dir: File): Boolean = archiveOps.run(action, files, dir)

        override fun archiveClipCount(): Int = archiveOps.clipCount

        override fun extractClipInto(dir: File): Boolean = archiveOps.extractClipInto(dir)

        override fun clearArchiveClip() = archiveOps.clearClip()

        // molina-27g: Properti dan Bagikan memakai PropsText/FilesShare/storage.properties yang sudah ada
        override fun properties(files: List<File>): Boolean = propsShare.properties(files)

        override fun share(files: List<File>): Boolean = propsShare.share(files)

        override fun openTrash() {
            if (childFragmentManager.findFragmentByTag(TRASH_TAG) != null) return
            TrashSheet().show(childFragmentManager, TRASH_TAG)
        }
    }

    // molina-27f: ArchiveSheetHost (lembar Lihat isi arsip)
    override val archiveStorage: MolinaStorage get() = storage

    override fun archiveBusy(): Boolean = FilesTransfer.isRunning || FilesArchive.isRunning

    override fun archiveFile(relative: String): File = File(paths.root, relative)

    override fun setSheetArchiveClip(archive: String, only: Set<String>?): Boolean = archiveOps.setSheetClip(archive, only)

    override fun viewArchiveEntry(relative: String): String? = archiveOps.viewEntry(relative)

    override fun startSheetExtract(
        archive: String,
        only: Set<String>?,
        target: ExtractTarget,
        policy: ConflictPolicy,
        password: String?,
    ): Boolean = archiveOps.startSheetExtract(archive, only, target, policy, password)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FrameLayout(requireContext())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // molina-27e: Back menutup mode pilih lebih dulu (aktif hanya saat mode pilih menyala)
        val cb = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                handle?.cancelSelection()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, cb)
        backCallback = cb
        val h = FileManagerEmbed.create(requireContext(), viewLifecycleOwner.lifecycleScope, actions) { picking ->
            backCallback?.isEnabled = picking
        }
        handle = h
        (view as FrameLayout).addView(h.view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        strip = buildStrip(view)
        FilesTransfer.useRoot(paths) // molina-27j
        FilesArchive.useRoot(paths)
        migrateFavorites27j()
        FilesArchive.sweepCache("start") // molina-27f
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { FilesTransfer.state.collect { renderTransfer(it) } }
                launch { FilesArchive.state.collect { renderArchive(it) } } // molina-27f
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val r = storage.purgeExpiredTrash()
            if (r is MolinaResult.Success && r.value > 0) FilesEvents.trashPurged("auto", r.value)
        }
    }

    /** [molina-27j] Favorit lama relatif terhadap Molina; root baru Internal, jadi diberi awalan "Molina/" sekali saja. */
    private fun migrateFavorites27j() {
        val flag = "berkas_fav_migrated_27j"
        if (explorerStore.getBoolean(flag, false)) return
        val old = ExplorerFavorites(ScopedKeyValueStore(explorerStore, MolinaPaths.DEFAULT_ROOT))
        for (p in old.paths()) favStore.add("Molina/$p")
        explorerStore.putBoolean(flag, true)
    }

    override fun onDestroy() { // molina-27f
        if (activity?.isFinishing == true) FilesArchive.sweepCache("exit")
        super.onDestroy()
    }

    override fun onDestroyView() {
        handle = null
        strip = null
        backCallback = null
        super.onDestroyView()
    }

    private fun buildStrip(host: FrameLayout): TransferStrip {
        val ctx = host.context
        val density = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val accent = ContextCompat.getColor(ctx, UiR.color.molina_terminal_accent)
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.setBackgroundColor(ContextCompat.getColor(ctx, UiR.color.molina_surface_variant))
        box.setPadding(dp(12), dp(6), dp(4), dp(8))
        box.isClickable = true // sentuhan tidak tembus ke daftar di belakang panel
        box.visibility = View.GONE
        val line = LinearLayout(ctx)
        line.orientation = LinearLayout.HORIZONTAL
        line.gravity = Gravity.CENTER_VERTICAL
        val text = TextView(ctx)
        text.setTextColor(0xFFFFFFFF.toInt())
        text.textSize = 12f
        text.maxLines = 1
        line.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val cancel = TextView(ctx)
        cancel.text = "Batal"
        cancel.textSize = 12f
        cancel.setTextColor(accent)
        cancel.setPadding(dp(12), dp(6), dp(12), dp(6))
        cancel.setOnClickListener { FilesTransfer.cancel(); FilesArchive.cancel() } // molina-27f
        line.addView(cancel)
        box.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val bar = LinearProgressIndicator(ctx)
        bar.max = 100
        bar.setIndicatorColor(accent)
        val barLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        barLp.marginEnd = dp(8)
        box.addView(bar, barLp)
        host.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        return TransferStrip(box, text, bar, cancel)
    }

    private fun renderTransfer(state: TransferState) {
        val s = strip ?: return
        when (state) {
            TransferState.Idle -> if (!FilesArchive.isRunning) s.root.visibility = View.GONE // molina-27f
            is TransferState.Running -> {
                s.root.visibility = View.VISIBLE
                s.text.text = TransferText.progressLine(state.kind, state.progress, state.cancelling)
                s.bar.setProgressCompat(((state.progress?.fraction ?: 0f) * 100).toInt(), true)
                s.cancel.isEnabled = !state.cancelling
            }
            is TransferState.Finished -> {
                s.root.visibility = View.GONE
                val text = state.result?.let { TransferText.summary(it) }
                    ?: state.error?.let { FilesErrorText.forTransfer(it.kind) }
                    ?: "Operasi gagal"
                view?.let { Snackbar.make(it, text, Snackbar.LENGTH_LONG).show() }
                handle?.transferFinished(clearClip = state.kind != TransferKind.DELETE && state.result != null)
                FilesTransfer.acknowledge(state.id)
            }
        }
    }

    /** [molina-27f] Panel progres yang sama dipakai arsip; hasil (Snackbar/dialog Uji/sandi) ditangani [BerkasArchive]. */
    private fun renderArchive(state: ArchiveState) {
        val s = strip ?: return
        when (state) {
            ArchiveState.Idle -> {
                if (!FilesTransfer.transferRunning) s.root.visibility = View.GONE
                archiveOps.promptNextPassword()
            }
            is ArchiveState.Running -> {
                s.root.visibility = View.VISIBLE
                s.text.text = ArchiveText.runningLine(state)
                s.bar.setProgressCompat(((state.progress?.fraction ?: 0f) * 100).toInt(), true)
                s.cancel.isEnabled = !state.cancelling
            }
            is ArchiveState.Finished -> {
                if (!FilesTransfer.transferRunning) s.root.visibility = View.GONE
                archiveOps.onFinished(state)
                FilesArchive.acknowledge(state.id)
            }
        }
    }

    private fun showUndo(ids: List<String>) {
        val v = view ?: return
        val text = if (ids.size == 1) "Dipindah ke Sampah" else "${ids.size} item dipindah ke Sampah"
        Snackbar.make(v, text, Snackbar.LENGTH_LONG).setDuration(UNDO_MS).setAction("Urungkan") { undo(ids) }.show()
    }

    private fun undo(ids: List<String>) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = storage.restoreTrash(ids)) {
                is MolinaResult.Success -> {
                    FilesEvents.trashRestored("undo", r.value.size)
                    handle?.refresh()
                }
                is MolinaResult.Failure -> {
                    FilesEvents.failed("trash-restore", r.error, "")
                    say(r.error.message)
                }
            }
        }
    }

    private fun say(text: String) {
        view?.let { Snackbar.make(it, text, Snackbar.LENGTH_SHORT).show() }
    }

    private companion object {
        const val TRASH_TAG = "berkas_trash"
        const val UNDO_MS = 6000
    }
}
