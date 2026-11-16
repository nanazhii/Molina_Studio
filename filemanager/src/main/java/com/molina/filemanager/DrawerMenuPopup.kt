// molina-27l
package com.molina.filemanager

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.molina.filemanager.EmbeddedMenuIcons.Glyph

/**
 * [molina-27l] Menu titik tiga tertanam dengan ikon. Pengganti PopupMenu: baris 38dp (sebelumnya 48dp), ikon 18dp,
 * lebar 160-220dp mengikuti isi, kelompok lokasi di bawah garis pemisah. Membuka ke atas dari tombol melayang,
 * rata kanan, dan bisa digulir bila lebih tinggi dari ruang yang tersedia.
 */
internal object DrawerMenuPopup {
    private const val ROW_DP = 38
    private const val ICON_DP = 18
    private const val PAD_DP = 12
    private const val MIN_W_DP = 160
    private const val MAX_W_DP = 220

    fun show(
        anchor: View,
        entries: List<EmbeddedMenu.Entry>,
        surface: Int,
        stroke: Int,
        accent: Int,
        danger: Int,
        onPick: (EmbeddedMenu.Entry) -> Unit,
    ) {
        if (entries.isEmpty()) return
        val ctx = anchor.context
        val dm = ctx.resources.displayMetrics
        fun dp(v: Int) = (v * dm.density).toInt()
        val soft = ColorUtils.setAlphaComponent(Color.WHITE, 0xDD)
        lateinit var popup: PopupWindow

        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.minimumWidth = dp(MIN_W_DP)
        col.setPadding(0, dp(4), 0, dp(4))
        entries.forEachIndexed { i, en ->
            val nav = EmbeddedMenuIcons.isNav(en.action)
            if (i > 0 && nav && !EmbeddedMenuIcons.isNav(entries[i - 1].action)) {
                val line = View(ctx)
                line.setBackgroundColor(ColorUtils.setAlphaComponent(Color.WHITE, 0x33))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                lp.setMargins(dp(10), dp(3), dp(10), dp(3))
                col.addView(line, lp)
            }
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(PAD_DP), 0, dp(PAD_DP), 0)
            row.background = RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 0x44)),
                null,
                ColorDrawable(Color.WHITE),
            )
            val icon = ImageView(ctx)
            icon.setImageDrawable(ContextCompat.getDrawable(ctx, resOf(EmbeddedMenuIcons.glyph(en))))
            icon.setColorFilter(if (en.danger) danger else if (nav) accent else soft, PorterDuff.Mode.SRC_IN)
            row.addView(icon, LinearLayout.LayoutParams(dp(ICON_DP), dp(ICON_DP)))
            val label = TextView(ctx)
            label.text = EmbeddedMenuIcons.label(en)
            label.textSize = 13f
            label.setTextColor(if (en.danger) danger else Color.WHITE)
            label.maxLines = 1
            label.ellipsize = TextUtils.TruncateAt.END
            label.maxWidth = dp(MAX_W_DP - PAD_DP * 2 - ICON_DP - PAD_DP)
            val llp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            llp.marginStart = dp(PAD_DP)
            row.addView(label, llp)
            if (en.enabled) {
                row.setOnClickListener {
                    popup.dismiss()
                    onPick(en)
                }
            } else {
                row.isEnabled = false
                row.alpha = 0.38f
            }
            col.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ROW_DP)))
        }

        val scroll = ScrollView(ctx)
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.addView(col)
        scroll.measure(
            View.MeasureSpec.makeMeasureSpec(dp(MAX_W_DP), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val room = (loc[1] - dp(24)).coerceAtLeast(dp(ROW_DP * 3))
        val h = minOf(scroll.measuredHeight, (dm.heightPixels * 0.7f).toInt(), room)

        popup = PopupWindow(scroll, scroll.measuredWidth, h, true)
        val bg = GradientDrawable()
        bg.setColor(surface)
        bg.cornerRadius = dp(12).toFloat()
        bg.setStroke(dp(1), stroke)
        popup.setBackgroundDrawable(bg)
        popup.elevation = dp(8).toFloat()
        popup.isOutsideTouchable = true
        popup.showAsDropDown(anchor, 0, -(anchor.height + h + dp(6)), Gravity.END)
    }

    private fun resOf(g: Glyph): Int = when (g) {
            Glyph.REFRESH -> R.drawable.ic_mnu_refresh
            Glyph.NEW_FOLDER -> R.drawable.ic_mnu_new_folder
            Glyph.NEW_FILE -> R.drawable.ic_mnu_new_file
            Glyph.EDIT -> R.drawable.ic_mnu_edit
            Glyph.CHECK -> R.drawable.ic_mnu_check
            Glyph.SELECT_ALL -> R.drawable.ic_mnu_select_all
            Glyph.CLOSE -> R.drawable.ic_mnu_close
            Glyph.COPY -> R.drawable.ic_mnu_copy
            Glyph.MOVE -> R.drawable.ic_mnu_move
            Glyph.PASTE -> R.drawable.ic_mnu_paste
            Glyph.ARCHIVE -> R.drawable.ic_mnu_archive
            Glyph.UNARCHIVE -> R.drawable.ic_mnu_unarchive
            Glyph.SUBTITLES -> R.drawable.ic_mnu_subtitles
            Glyph.VIEW -> R.drawable.ic_mnu_view
            Glyph.SHARE -> R.drawable.ic_mnu_share
            Glyph.STAR -> R.drawable.ic_mnu_star
            Glyph.LINK -> R.drawable.ic_mnu_link
            Glyph.INFO -> R.drawable.ic_mnu_info
            Glyph.TRASH -> R.drawable.ic_mnu_trash
            Glyph.DELETE_FOREVER -> R.drawable.ic_mnu_delete_forever
            Glyph.SEARCH -> R.drawable.ic_mnu_search
            Glyph.SORT -> R.drawable.ic_mnu_sort
            Glyph.EYE -> R.drawable.ic_mnu_eye
            Glyph.EYE_OFF -> R.drawable.ic_mnu_eye_off
            Glyph.PHONE -> R.drawable.ic_mnu_phone
            Glyph.TERMINAL -> R.drawable.ic_mnu_terminal
            Glyph.DOWNLOAD -> R.drawable.ic_mnu_download
            Glyph.PHOTO -> R.drawable.ic_mnu_photo
            Glyph.DOCS -> R.drawable.ic_mnu_docs
    }
}
