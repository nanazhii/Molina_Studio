// molina-27d
package com.molina.files

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.molina.core.common.MolinaPaths
import com.molina.core.common.MolinaResult
import com.molina.core.common.getOrNull
import com.molina.core.storage.ExplorerFavorites
import com.molina.core.storage.LocalMolinaStorage
import com.molina.core.storage.MolinaStorage
import com.molina.core.storage.PathGuard
import com.molina.core.storage.ScopedKeyValueStore
import com.molina.core.storage.SharedPrefsKeyValueStore
import com.molina.filemanager.FileManagerActions
import com.molina.filemanager.FileManagerEmbed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [molina-27b] Tab Berkas memakai file manager salinan Terminal. FilesFragment lama tetap ada (balik: 1 baris di MainActivity).
 * [molina-27d] Favorit dan Sampah memakai ExplorerFavorites/TrashBin/TrashSheet yang sudah ada; tidak ada logika yang disalin ulang.
 */
class BerkasFragment : Fragment() {
    private val paths = MolinaPaths()
    private val storage: MolinaStorage by lazy { LocalMolinaStorage(paths) }
    private val explorerStore by lazy { SharedPrefsKeyValueStore(requireContext()) }
    // Favorit memakai path relatif terhadap root, sama dengan FilesFragment, jadi daftarnya dibagi.
    private val favStore by lazy { ExplorerFavorites(ScopedKeyValueStore(explorerStore, paths.root.path)) }
    private var handle: FileManagerEmbed.Handle? = null

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

        override fun openTrash() {
            if (childFragmentManager.findFragmentByTag(TRASH_TAG) != null) return
            TrashSheet().show(childFragmentManager, TRASH_TAG)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FrameLayout(requireContext())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val h = FileManagerEmbed.create(requireContext(), viewLifecycleOwner.lifecycleScope, actions)
        handle = h
        (view as FrameLayout).addView(h.view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        viewLifecycleOwner.lifecycleScope.launch {
            val r = storage.purgeExpiredTrash()
            if (r is MolinaResult.Success && r.value > 0) FilesEvents.trashPurged("auto", r.value)
        }
    }

    override fun onDestroyView() {
        handle = null
        super.onDestroyView()
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
