// molina-28b
package com.molina.core.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.text.format.Formatter
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils

/**
 * [molina-28b] Blok teks satu baris pohon drawer: [name] di atas; bila [twoLine], di bawahnya tanggal ubah (kiri)
 * dan jumlah item / ukuran (kanan). Tanggal yang baru (24 jam terakhir) berwarna hijau lembut.
 * Dipakai bersama oleh drawer Berkas dan (kelak) Terminal. Gaya [name] diatur pemanggil.
 */
class EntryTextBlock(context: Context, private val twoLine: Boolean) : LinearLayout(context) {
    val name = TextView(context)
    private val date = TextView(context)
    private val meta = TextView(context)
    private val dim = ColorUtils.setAlphaComponent(Color.WHITE, 0x99)
    private val fresh = ColorUtils.setAlphaComponent(ContextCompat.getColor(context, R.color.molina_key_green), 0xCC)

    init {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        addView(name, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (twoLine) {
            val line = LinearLayout(context)
            line.orientation = LinearLayout.HORIZONTAL
            line.gravity = Gravity.CENTER_VERTICAL
            styleMeta(date)
            styleMeta(meta)
            meta.gravity = Gravity.END
            meta.setPadding(dp(8), 0, dp(10), 0)
            line.addView(date, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            line.addView(meta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    /** Isi keterangan baris. Tanpa efek bila bukan [twoLine]. */
    fun bindMeta(modified: Long, isDir: Boolean, childCount: Int, size: Long, now: Long = System.currentTimeMillis()) {
        if (!twoLine) return
        date.text = EntryInfo.dateLabel(modified, now)
        date.setTextColor(if (EntryInfo.isFresh(modified, now)) fresh else dim)
        meta.text = if (isDir) EntryInfo.countLabel(childCount) else Formatter.formatShortFileSize(context, size)
    }

    private fun styleMeta(v: TextView) {
        v.setTextColor(dim)
        v.textSize = DrawerMetrics.META_SP
        v.typeface = Typeface.MONOSPACE
        v.maxLines = 1
        v.ellipsize = TextUtils.TruncateAt.END
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
}
