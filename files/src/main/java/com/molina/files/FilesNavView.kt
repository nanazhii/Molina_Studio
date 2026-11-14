// molina-patch26f
package com.molina.files

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.molina.core.ui.R as UiR

/** Menggambar isi [FilesNav] ke daftar vertikal laci kiri. Aksen: bintang emas, item aktif hijau muda tebal. */
internal class FilesNavView(
    private val list: LinearLayout,
    private val onFolder: (String) -> Unit,
    private val onRemove: (String) -> Unit,
    private val onDebian: () -> Unit,
    private val onTrash: () -> Unit,
) {
    fun render(items: List<FilesNav.Item>) {
        val ctx = list.context
        list.removeAllViews()
        items.forEachIndexed { index, item ->
            val row: View = when (item.kind) {
                FilesNav.Kind.HEADER -> header(ctx, item, first = index == 0)
                FilesNav.Kind.FOLDER -> folder(ctx, item)
                FilesNav.Kind.DEBIAN -> debian(ctx)
                FilesNav.Kind.TRASH -> trash(ctx)
            }
            list.addView(
                row,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
    }

    private fun px(ctx: Context, dp: Int): Int = (dp * ctx.resources.displayMetrics.density).toInt()

    private fun header(ctx: Context, item: FilesNav.Item, first: Boolean): View {
        val text = SpannableString(if (item.star) "\u2605 ${item.label}" else item.label)
        if (item.star) {
            text.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(ctx, R.color.files_star_gold)),
                0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return TextView(ctx).apply {
            this.text = text
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, UiR.color.molina_on_surface_variant))
            setPadding(px(ctx, 12), px(ctx, if (first) 4 else 16), px(ctx, 12), px(ctx, 4))
        }
    }

    private fun folder(ctx: Context, item: FilesNav.Item): View {
        val path = item.path.orEmpty()
        val active = item.active
        val ink = ContextCompat.getColor(
            ctx, if (active) R.color.files_nav_active else UiR.color.molina_on_surface,
        )
        return TextView(ctx).apply {
            text = item.label
            textSize = 14f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(ink)
            setTypeface(typeface, if (active) Typeface.BOLD else Typeface.NORMAL)
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_files_folder, 0, 0, 0)
            compoundDrawableTintList = ColorStateList.valueOf(ink)
            compoundDrawablePadding = px(ctx, 10)
            setPadding(px(ctx, 12), px(ctx, 10), px(ctx, 12), px(ctx, 10))
            if (active) {
                setBackgroundResource(R.color.files_row_selected)
            } else {
                val out = TypedValue()
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
                setBackgroundResource(out.resourceId)
            }
            setOnClickListener { onFolder(path) }
            if (item.removable) {
                setOnLongClickListener {
                    onRemove(path)
                    true
                }
            }
        }
    }

    // molina-patch26h
    private fun trash(ctx: Context): View {
        val ink = ContextCompat.getColor(ctx, UiR.color.molina_on_surface)
        return TextView(ctx).apply {
            text = "Sampah"
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(ink)
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_files_trash, 0, 0, 0)
            compoundDrawableTintList = ColorStateList.valueOf(ink)
            compoundDrawablePadding = px(ctx, 10)
            setPadding(px(ctx, 12), px(ctx, 10), px(ctx, 12), px(ctx, 10))
            val out = TypedValue()
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
            setBackgroundResource(out.resourceId)
            setOnClickListener { onTrash() }
        }
    }

    /** Debian belum bisa dijelajahi (di luar root Molina): titik kuning + teks, bukan hanya warna. */
    private fun debian(ctx: Context): View {
        val text = SpannableString("\u25CF  Debian \u00B7 di luar root")
        text.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(ctx, UiR.color.molina_cinema_accent)),
            0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        return TextView(ctx).apply {
            this.text = text
            textSize = 14f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(ContextCompat.getColor(ctx, UiR.color.molina_on_surface_variant))
            setPadding(px(ctx, 12), px(ctx, 10), px(ctx, 12), px(ctx, 10))
            setOnClickListener { onDebian() }
        }
    }
}
