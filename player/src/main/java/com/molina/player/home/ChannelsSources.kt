package com.molina.player.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.view.updateLayoutParams
import com.molina.core.common.diag.Diag
import com.molina.core.common.home.FileSizeLabel
import com.molina.core.security.UrlPolicy
import com.molina.player.PlaybackLog
import com.molina.player.R
import com.molina.player.databinding.DialogChannelsBinding
import com.molina.player.databinding.ItemChannelChipBinding
import com.molina.player.databinding.ItemChannelFileBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * Baris chip sumber (playlist tersimpan, + Tambah, Berkas), panel tambah tautan, dan panel berkas folder IPTV.
 * Mengelola daftar sumber lewat [ChannelsRepository] dan melaporkan sumber yang perlu dimuat lewat [onLoad];
 * tidak memegang daftar saluran. [onOpenInEditor] null menyembunyikan tombol Code di baris berkas.
 */
internal class ChannelsSources(
    private val binding: DialogChannelsBinding,
    private val repo: ChannelsRepository,
    private val scope: CoroutineScope,
    private val onLoad: (ref: String, force: Boolean) -> Unit,
    private val onEmpty: () -> Unit,
    private val onProblem: (@StringRes Int) -> Unit,
    private val onOpenInEditor: ((File) -> Unit)? = null,
) {
    /** Sumber yang sedang aktif; null bila belum ada sumber. */
    var current: String? = null
        private set

    private var adding = false
    private var picking = false
    private var filesBusy = false
    private var files: List<LocalPlaylist>? = emptyList() // null = folder tak terbaca
    private val ctx = binding.root.context
    private val inflater = LayoutInflater.from(ctx)

    fun attach() {
        val sources = repo.sources()
        current = repo.lastSource?.takeIf { s -> sources.any { it.ref == s } } ?: sources.firstOrNull()?.ref
        adding = sources.isEmpty()

        binding.folderPath.text = ctx.getString(R.string.channels_folder_label, displayPath(repo.folder))
        binding.addSave.setOnClickListener { saveUrl() }
        binding.addInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                saveUrl()
                true
            } else {
                false
            }
        }
        binding.folderOpen.setOnClickListener { revealFolder() }
        binding.filesRefresh.setOnClickListener { refreshFiles() }

        render()
        val ref = current
        if (ref != null) onLoad(ref, false) else onEmpty()
    }

    /** Menghapus sumber aktif dari daftar (berkasnya sendiri tidak dihapus) dan pindah ke sumber berikutnya. */
    fun removeCurrent() {
        val ref = current ?: return
        repo.removeSource(ref)
        Diag.behavior("player", "channels-source-removed", "sumber daftar dihapus",
            mapOf("target" to PlaybackLog.target(ref)))
        val next = repo.sources().firstOrNull()?.ref
        current = next
        repo.lastSource = next
        if (next == null) {
            adding = true
            picking = false
        }
        render()
        if (next != null) onLoad(next, false) else onEmpty()
    }

    private fun render() {
        val row = binding.chipRow
        row.removeAllViews()
        for (s in repo.sources()) {
            row.addView(chip(s.name, s.ref == current && !adding && !picking) { select(s.ref) })
        }
        row.addView(chip(ctx.getString(R.string.channels_chip_add), adding) { toggleAdd() })
        row.addView(chip(ctx.getString(R.string.channels_chip_files), picking) { togglePicker() })
        binding.addPanel.visibility = if (adding) View.VISIBLE else View.GONE
        binding.pickPanel.visibility = if (picking) View.VISIBLE else View.GONE
        renderFiles()
    }

    private fun chip(label: String, active: Boolean, onClick: () -> Unit): View {
        val v = ItemChannelChipBinding.inflate(inflater, binding.chipRow, false)
        v.root.text = label
        v.root.isSelected = active
        v.root.setOnClickListener { onClick() }
        return v.root
    }

    private fun select(ref: String) {
        adding = false
        picking = false
        current = ref
        repo.lastSource = ref
        render()
        onLoad(ref, false)
    }

    private fun toggleAdd() {
        adding = !adding
        if (adding) picking = false
        render()
        if (adding) binding.addInput.requestFocus()
    }

    private fun togglePicker() {
        picking = !picking
        if (picking) {
            adding = false
            refreshFiles()
        }
        render()
    }

    private fun saveUrl() {
        val text = binding.addInput.text.toString().trim()
        if (!M3uFetcher.isHttp(text) || UrlPolicy.check(text) !is UrlPolicy.Verdict.Allowed) {
            onProblem(R.string.channels_url_invalid)
            return
        }
        if (addSource(text)) binding.addInput.setText("")
    }

    private fun addSource(ref: String): Boolean {
        try {
            repo.addSource(ref)
        } catch (e: IllegalArgumentException) {
            onProblem(R.string.channels_url_invalid)
            return false
        }
        Diag.behavior("player", "channels-source-added", "sumber daftar ditambah", mapOf(
            "kind" to if (ref.startsWith("/")) "file" else "url", "target" to PlaybackLog.target(ref),
        ))
        current = ref
        adding = false
        picking = false
        render()
        onLoad(ref, true)
        return true
    }

    private fun refreshFiles() {
        if (filesBusy) return
        filesBusy = true
        renderFiles()
        scope.launch {
            files = repo.listFiles()
            filesBusy = false
            if (binding.root.isAttachedToWindow) renderFiles()
        }
    }

    private fun renderFiles() {
        val list = files
        val message = when {
            filesBusy && list.isNullOrEmpty() -> R.string.channels_files_reading
            list == null -> R.string.channels_files_unreadable
            list.isEmpty() -> R.string.channels_files_empty
            else -> 0
        }
        binding.fileEmpty.visibility = if (message != 0) View.VISIBLE else View.GONE
        if (message != 0) binding.fileEmpty.setText(message)
        binding.fileScroll.visibility = if (message == 0) View.VISIBLE else View.GONE
        binding.fileList.removeAllViews()
        if (list == null || message != 0) return
        for (f in list) binding.fileList.addView(fileRow(f))
        val maxPx = (MAX_FILES_DP * ctx.resources.displayMetrics.density).toInt()
        binding.fileScroll.updateLayoutParams {
            height = if (list.size > SCROLL_AFTER_ROWS) maxPx else ViewGroup.LayoutParams.WRAP_CONTENT
        }
    }

    private fun fileRow(f: LocalPlaylist): View {
        val v = ItemChannelFileBinding.inflate(inflater, binding.fileList, false)
        v.fileName.text = f.file.name
        v.fileSize.text = FileSizeLabel.of(f.sizeBytes)
        val editor = onOpenInEditor
        if (editor != null) {
            v.fileCode.visibility = View.VISIBLE
            v.fileCode.setOnClickListener { editor(f.file) }
        }
        v.root.setOnClickListener { addSource(f.file.absolutePath) }
        return v.root
    }

    private fun revealFolder() {
        repo.folder.mkdirs()
        Toast.makeText(ctx, ctx.getString(R.string.channels_folder_hint, displayPath(repo.folder)), Toast.LENGTH_LONG).show()
    }

    private fun displayPath(f: File): String = f.path.replaceFirst("/storage/emulated/0", "/sdcard")

    private companion object {
        const val MAX_FILES_DP = 190
        const val SCROLL_AFTER_ROWS = 4
    }
}
