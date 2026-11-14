// molina-patch26h
package com.molina.files

import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.molina.core.common.MolinaResult
import com.molina.core.storage.TrashBin
import com.molina.core.storage.TrashItem
import com.molina.core.ui.MolinaDialog
import kotlinx.coroutines.launch
import com.molina.core.ui.R as UiR

/**
 * Layar Sampah (lembar bawah) milik [FilesFragment]: daftar item, ketuk untuk Pulihkan / Hapus permanen,
 * dan Kosongkan. Tidak mencatat nama item di log. Dibuat lewat childFragmentManager Berkas.
 */
internal class TrashSheet : BottomSheetDialogFragment() {
    private var list: LinearLayout? = null
    private var emptyNote: TextView? = null
    private var clearBtn: TextView? = null

    private val host: FilesFragment? get() = parentFragment as? FilesFragment

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val white = ContextCompat.getColor(ctx, UiR.color.molina_on_surface)
        val dim = ContextCompat.getColor(ctx, UiR.color.molina_on_surface_variant)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(ctx, UiR.color.molina_bg))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(
            TextView(ctx).apply {
                text = "Sampah"
                textSize = 18f
                setTextColor(white)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        val clear = TextView(ctx).apply {
            text = "Kosongkan"
            textSize = 14f
            setTextColor(ContextCompat.getColor(ctx, UiR.color.molina_cinema_accent))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { confirmEmpty() }
        }
        clearBtn = clear
        head.addView(clear)
        root.addView(head)
        root.addView(
            TextView(ctx).apply {
                text = "Item dihapus otomatis setelah ${TrashBin.RETAIN_DAYS} hari."
                textSize = 12f
                setTextColor(dim)
                setPadding(0, dp(2), 0, dp(8))
            },
        )
        val note = TextView(ctx).apply {
            text = "Sampah kosong"
            textSize = 14f
            setTextColor(dim)
            setPadding(0, dp(24), 0, dp(24))
            visibility = View.GONE
        }
        emptyNote = note
        root.addView(note)
        val items = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        list = items
        root.addView(
            NestedScrollView(ctx).apply { addView(items) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refresh()
    }

    override fun onDestroyView() {
        list = null
        emptyNote = null
        clearBtn = null
        super.onDestroyView()
    }

    private fun refresh() {
        val h = host
        if (h == null) {
            dismissAllowingStateLoss()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = h.trashStorage.listTrash()) {
                is MolinaResult.Success -> render(r.value)
                is MolinaResult.Failure -> {
                    FilesEvents.failed("trash-list", r.error, "")
                    render(emptyList())
                    toast("Sampah tidak bisa dibaca")
                }
            }
        }
    }

    private fun render(items: List<TrashItem>) {
        val ctx = context ?: return
        val box = list ?: return
        val d = ctx.resources.displayMetrics.density
        val white = ContextCompat.getColor(ctx, UiR.color.molina_on_surface)
        val dim = ContextCompat.getColor(ctx, UiR.color.molina_on_surface_variant)
        box.removeAllViews()
        emptyNote?.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        clearBtn?.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        val now = System.currentTimeMillis()
        for (item in items) {
            val where = item.originParent.ifEmpty { "Molina" }
            val line2 = "\n$where \u00B7 ${item.daysLeft(now)} hari lagi"
            val text = SpannableString(item.name + line2)
            text.setSpan(ForegroundColorSpan(dim), item.name.length, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(RelativeSizeSpan(0.85f), item.name.length, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val row = TextView(ctx).apply {
                this.text = text
                textSize = 15f
                setTextColor(white)
                maxLines = 3
                setPadding((8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt())
            }
            row.setOnClickListener { showActions(item, row) }
            box.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun showActions(item: TrashItem, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(Menu.NONE, ACT_RESTORE, 0, "Pulihkan")
        popup.menu.add(Menu.NONE, ACT_PURGE, 1, "Hapus permanen")
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                ACT_RESTORE -> restore(item)
                ACT_PURGE -> confirmPurge(item)
            }
            true
        }
        popup.show()
    }

    private fun restore(item: TrashItem) {
        val h = host ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = h.trashStorage.restoreTrash(listOf(item.id))) {
                is MolinaResult.Success -> {
                    FilesEvents.trashRestored("sheet", r.value.size)
                    toast("Dipulihkan")
                    h.onTrashChanged()
                }
                is MolinaResult.Failure -> {
                    FilesEvents.failed("trash-restore", r.error, "")
                    toast(FilesErrorText.forOp(r.error.kind))
                }
            }
            refresh()
        }
    }

    private fun confirmPurge(item: TrashItem) {
        MolinaDialog.confirm(
            requireContext(),
            "Hapus permanen?",
            "\"${item.name}\" akan dihapus selamanya dari Sampah.",
            "Hapus permanen",
        ) { purge(listOf(item.id), "sheet") }
    }

    private fun confirmEmpty() {
        MolinaDialog.confirm(
            requireContext(),
            "Kosongkan Sampah?",
            "Semua item di Sampah akan dihapus selamanya.",
            "Kosongkan",
        ) {
            val h = host ?: return@confirm
            viewLifecycleOwner.lifecycleScope.launch {
                when (val r = h.trashStorage.emptyTrash()) {
                    is MolinaResult.Success -> FilesEvents.trashPurged("empty", r.value)
                    is MolinaResult.Failure -> {
                        FilesEvents.failed("trash-purge", r.error, "")
                        toast(FilesErrorText.forOp(r.error.kind))
                    }
                }
                refresh()
            }
        }
    }

    private fun purge(ids: List<String>, via: String) {
        val h = host ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = h.trashStorage.purgeTrash(ids)) {
                is MolinaResult.Success -> FilesEvents.trashPurged(via, r.value)
                is MolinaResult.Failure -> {
                    FilesEvents.failed("trash-purge", r.error, "")
                    toast(FilesErrorText.forOp(r.error.kind))
                }
            }
            refresh()
        }
    }

    private fun toast(text: String) {
        context?.let { Toast.makeText(it, text, Toast.LENGTH_SHORT).show() }
    }

    private companion object {
        const val ACT_RESTORE = 1
        const val ACT_PURGE = 2
    }
}
