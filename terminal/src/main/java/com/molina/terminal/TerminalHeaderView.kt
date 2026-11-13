// molina-patch20
package com.molina.terminal

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.molina.core.ui.R as UiR

/**
 * Header Terminal: bingkai hijau, ikon folder (buka drawer berkas), kotak path yang MELEBAR sampai
 * tombol titik tiga (bobot 1, bukan lebar tetap), dan titik tiga dengan titik status sesi.
 */
internal class TerminalHeaderView(
    context: Context,
    private val onFolder: () -> Unit,
    private val onMenu: (View) -> Unit,
) : LinearLayout(context) {

    private val pathBox = TextView(context)
    private val dots = HeaderDotsView(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(2), dp(2), dp(2), dp(2))
        val frame = GradientDrawable()
        frame.setColor(ContextCompat.getColor(context, UiR.color.molina_extrakey_bg))
        frame.cornerRadius = dp(8).toFloat()
        frame.setStroke(dp(2), ContextCompat.getColor(context, UiR.color.molina_key_green))
        background = frame

        val folder = FrameLayout(context)
        folder.isClickable = true
        folder.contentDescription = context.getString(R.string.drawer_open_desc)
        folder.background = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(ContextCompat.getColor(context, UiR.color.molina_terminal_accent), 0x55)),
            null,
            ColorDrawable(Color.WHITE),
        )
        folder.setOnClickListener { onFolder() }
        val icon = TreeIconView(context)
        icon.isDir = true
        folder.addView(icon, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
        addView(folder, LinearLayout.LayoutParams(dp(44), dp(44)))

        val box = GradientDrawable()
        box.setColor(ContextCompat.getColor(context, UiR.color.molina_bg))
        box.cornerRadius = dp(4).toFloat()
        box.setStroke(dp(1), ContextCompat.getColor(context, UiR.color.molina_outline))
        pathBox.background = box
        pathBox.setTextColor(Color.WHITE)
        pathBox.textSize = 14f
        pathBox.maxLines = 1
        pathBox.ellipsize = TextUtils.TruncateAt.MIDDLE
        pathBox.gravity = Gravity.CENTER_VERTICAL
        pathBox.setPadding(dp(12), 0, dp(12), 0)
        pathBox.setOnClickListener { onFolder() }
        val boxLp = LinearLayout.LayoutParams(0, dp(36), 1f)
        boxLp.marginStart = dp(2)
        boxLp.marginEnd = dp(4)
        addView(pathBox, boxLp)

        dots.contentDescription = context.getString(R.string.terminal_menu_desc)
        dots.setOnClickListener { onMenu(dots) }
        addView(dots, LinearLayout.LayoutParams(dp(44), dp(44)))
    }

    private var rootLabel: String = ""
    private var cwdLabel: String? = null

    /** Cadangan bila sesi tidak melaporkan path (shell Android): folder utama drawer. */
    fun setPath(path: String) {
        rootLabel = DrawerFs.displayPath(path)
        render()
    }

    /** Path aktif terminal, mis. user@localhost:/sdcard; null bila sesi tidak melaporkannya. */
    fun setCwd(label: String?) {
        cwdLabel = label
        render()
    }

    private fun render() {
        val label = cwdLabel ?: rootLabel
        if (pathBox.text.toString() != label) pathBox.text = label
    }

    fun setStatus(color: Int) {
        dots.statusColor = color
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
}

/** Tiga titik vertikal; [statusColor] non-null menggambar titik status di pojok kanan atas. */
internal class HeaderDotsView(context: Context) : View(context) {
    var statusColor: Int? = null
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val status = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        dot.color = ColorUtils.setAlphaComponent(Color.WHITE, 0xDD)
        isClickable = true
        background = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(ContextCompat.getColor(context, UiR.color.molina_terminal_accent), 0x55)),
            null,
            ColorDrawable(Color.WHITE),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        for (i in -1..1) canvas.drawCircle(cx, cy + i * 7f * d, 2.2f * d, dot)
        statusColor?.let {
            status.color = it
            canvas.drawCircle(cx + 8f * d, cy - 12f * d, 4.5f * d, status)
        }
    }
}
