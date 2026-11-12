// molina-patch9c
package com.molina.terminal

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.molina.core.ui.R as UiR
import kotlin.math.max
import kotlin.math.min

private class MenuEntry(val label: String, val mono: Boolean, val tint: Int?, val action: () -> Unit)

private const val MENU_MIN_WIDTH_DP = 168
private const val MENU_MAX_WIDTH_DP = 260
private const val MENU_MIN_HEIGHT_DP = 120

/**
 * Menu tombol hamburger: simbol yang sulit diketik, END/PGUP/PGDN, dan mulai ulang sesi.
 * Berupa PopupWindow dengan ScrollView: tingginya dibatasi ruang di atas tombol (sempit saat keyboard
 * tampil), sisanya digulir. Tidak mengambil fokus sehingga keyboard tetap terbuka.
 */
internal fun showTerminalMenu(anchor: View, actions: TerminalKeyActions, onRestart: () -> Unit) {
    val ctx = anchor.context
    val density = ctx.resources.displayMetrics.density
    fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    val accent = ContextCompat.getColor(ctx, UiR.color.molina_terminal_accent)
    val surface = ContextCompat.getColor(ctx, UiR.color.molina_surface_variant)
    val outline = ContextCompat.getColor(ctx, UiR.color.molina_outline)
    val onSurface = ContextCompat.getColor(ctx, UiR.color.molina_on_surface)

    var popup: PopupWindow? = null

    val entries = buildList<MenuEntry> {
        listOf("/", "|", "~", "\\", "_", "&").forEach { s ->
            add(MenuEntry(s, true, null) { actions.send(s) })
        }
        add(MenuEntry("END", true, null) { actions.sendKey(KeyEvent.KEYCODE_MOVE_END) })
        add(MenuEntry("PGUP", true, null) { actions.sendKey(KeyEvent.KEYCODE_PAGE_UP) })
        add(MenuEntry("PGDN", true, null) { actions.sendKey(KeyEvent.KEYCODE_PAGE_DOWN) })
        add(MenuEntry(ctx.getString(R.string.terminal_restart_session), false, accent, onRestart))
    }

    val column = LinearLayout(ctx)
    column.orientation = LinearLayout.VERTICAL
    entries.forEachIndexed { index, entry ->
        if (index == entries.lastIndex) {
            val divider = View(ctx)
            divider.setBackgroundColor(outline)
            column.addView(divider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))
        }
        val row = TextView(ctx)
        row.text = entry.label
        row.textSize = 16f
        row.typeface = if (entry.mono) Typeface.MONOSPACE else Typeface.DEFAULT_BOLD
        row.setTextColor(entry.tint ?: onSurface)
        row.minWidth = dp(MENU_MIN_WIDTH_DP)
        row.setPadding(dp(16), dp(10), dp(16), dp(10))
        row.background = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 0x55)),
            null,
            ColorDrawable(Color.WHITE),
        )
        row.setOnClickListener {
            popup?.dismiss()
            entry.action()
        }
        column.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    val scroll = ScrollView(ctx)
    val shape = GradientDrawable()
    shape.setColor(surface)
    shape.cornerRadius = dp(12).toFloat()
    shape.setStroke(dp(1), outline)
    scroll.background = shape
    scroll.clipToOutline = true
    scroll.isVerticalScrollBarEnabled = true
    scroll.overScrollMode = View.OVER_SCROLL_NEVER
    scroll.addView(column)

    scroll.measure(
        View.MeasureSpec.makeMeasureSpec(dp(MENU_MAX_WIDTH_DP), View.MeasureSpec.AT_MOST),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
    )
    val frame = Rect()
    anchor.getWindowVisibleDisplayFrame(frame)
    val location = IntArray(2)
    anchor.getLocationOnScreen(location)

    val availableAbove = location[1] - frame.top - dp(8)
    val height = min(scroll.measuredHeight, max(availableAbove, dp(MENU_MIN_HEIGHT_DP)))
    val width = scroll.measuredWidth
    val x = location[0].coerceIn(frame.left + dp(4), max(frame.left + dp(4), frame.right - width - dp(4)))
    val y = max(frame.top, location[1] - height - dp(4))

    val window = PopupWindow(scroll, width, height, false)
    window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    window.isOutsideTouchable = true
    window.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
    popup = window
    window.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
}
