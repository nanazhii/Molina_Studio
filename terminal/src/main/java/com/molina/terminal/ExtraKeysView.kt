// molina-patch9
package com.molina.terminal

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.molina.core.ui.R as UiR

/**
 * Baris tombol ekstra dua lapis. Panel berlatar molina_terminal_container dengan aksen teal;
 * CTRL/ALT menyala saat aktif (ketuk) atau terkunci (tekan lama). Panah menahan = ulang otomatis.
 * Tombol ≡ memanggil [onMenu] (menu simbol + mulai ulang sesi).
 */
internal class ExtraKeysView(
    context: Context,
    private val mods: TerminalModifiers,
    private val actions: TerminalKeyActions,
    private val onMenu: (View) -> Unit,
) : LinearLayout(context) {

    private val accent = color(UiR.color.molina_terminal_accent)
    private val container = color(UiR.color.molina_terminal_container)
    private val outline = color(UiR.color.molina_outline)
    private val plain = color(UiR.color.molina_on_surface)
    private val danger = color(UiR.color.molina_key_red)
    private val green = color(UiR.color.molina_key_green)
    private val blue = color(UiR.color.molina_key_blue)

    private val ctrlKey: TextView
    private val altKey: TextView
    private var repeatRunner: Runnable? = null

    init {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), dp(3), dp(8), dp(3))
        background = panelBackground(lit = false)

        ctrlKey = key("CTRL", "Ctrl", plain, onLong = { mods.toggleCtrlLock() }) { mods.tapCtrl() }
        altKey = key("ALT", "Alt", plain, onLong = { mods.toggleAltLock() }) { mods.tapAlt() }

        addView(
            row(
                key("ESC", "Escape", danger) { actions.send("\u001b") },
                key("\u2261", "Menu simbol", accent) { }.also { menuKey ->
                    menuKey.setOnClickListener {
                        menuKey.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onMenu(menuKey)
                    }
                    menuKey.setOnLongClickListener {
                        menuKey.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onMenu(menuKey)
                        true
                    }
                },
                key("HOME", "Home", blue) { actions.sendKey(KeyEvent.KEYCODE_MOVE_HOME) },
                key("CLEAR", "Bersihkan layar", danger) { actions.clear() },
                key("\u2191", "Panah atas", blue, repeat = true) { actions.sendKey(KeyEvent.KEYCODE_DPAD_UP) },
                key("PASTE", "Tempel", green) { actions.paste() },
                key("COPY", "Salin semua", green) {
                    val ok = actions.copyAll()
                    Toast.makeText(
                        context,
                        if (ok) R.string.terminal_copy_done else R.string.terminal_copy_empty,
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            ),
        )
        addView(
            row(
                key("\u21C6", "Tab", accent) { actions.send("\t") },
                ctrlKey,
                altKey,
                key("\u2190", "Panah kiri", blue, repeat = true) { actions.sendKey(KeyEvent.KEYCODE_DPAD_LEFT) },
                key("\u2193", "Panah bawah", blue, repeat = true) { actions.sendKey(KeyEvent.KEYCODE_DPAD_DOWN) },
                key("\u2192", "Panah kanan", blue, repeat = true) { actions.sendKey(KeyEvent.KEYCODE_DPAD_RIGHT) },
                key("ENTER", "Enter", accent) { actions.sendKey(KeyEvent.KEYCODE_ENTER) },
            ),
        )
        refresh()
    }

    /** Gambar ulang status CTRL/ALT dan bingkai panel. */
    fun refresh() {
        styleKey(ctrlKey, plain, mods.ctrl, mods.ctrlLocked)
        styleKey(altKey, plain, mods.alt, mods.altLocked)
        background = panelBackground(lit = mods.ctrl || mods.alt)
    }

    override fun onDetachedFromWindow() {
        stopRepeat()
        super.onDetachedFromWindow()
    }

    private fun row(vararg keys: TextView): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        keys.forEach { k ->
            val lp = LinearLayout.LayoutParams(0, dp(KEY_HEIGHT_DP), 1f)
            lp.setMargins(dp(1), 0, dp(1), 0)
            addView(k, lp)
        }
    }

    private fun key(
        label: String,
        description: String,
        tint: Int,
        repeat: Boolean = false,
        onLong: (() -> Unit)? = null,
        onClick: () -> Unit,
    ): TextView {
        val tv = TextView(context)
        tv.text = label
        tv.contentDescription = description
        tv.gravity = Gravity.CENTER
        tv.textSize = 13f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.maxLines = 1
        tv.isFocusable = false
        tv.isFocusableInTouchMode = false
        styleKey(tv, tint, active = false, locked = false)
        if (repeat) {
            tv.setOnTouchListener { v, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onClick()
                        startRepeat(onClick)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        stopRepeat()
                    }
                }
                true
            }
        } else {
            tv.setOnClickListener {
                tv.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
            if (onLong != null) {
                tv.setOnLongClickListener {
                    tv.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onLong()
                    true
                }
            }
        }
        return tv
    }

    private fun startRepeat(action: () -> Unit) {
        stopRepeat()
        val runner = object : Runnable {
            override fun run() {
                action()
                postDelayed(this, REPEAT_MS)
            }
        }
        repeatRunner = runner
        postDelayed(runner, REPEAT_DELAY_MS)
    }

    private fun stopRepeat() {
        repeatRunner?.let { removeCallbacks(it) }
        repeatRunner = null
    }

    private fun styleKey(tv: TextView, tint: Int, active: Boolean, locked: Boolean) {
        val fill = when {
            locked -> ColorUtils.setAlphaComponent(accent, 0x66)
            active -> ColorUtils.setAlphaComponent(accent, 0x38)
            else -> Color.TRANSPARENT
        }
        val strokeWidth = when {
            locked -> dp(2)
            active -> dp(1)
            else -> 0
        }
        val content = GradientDrawable()
        content.setColor(fill)
        content.cornerRadius = dp(8).toFloat()
        if (strokeWidth > 0) content.setStroke(strokeWidth, accent)
        val mask = GradientDrawable()
        mask.setColor(Color.WHITE)
        mask.cornerRadius = dp(8).toFloat()
        tv.background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 0x55)), content, mask)
        tv.setTextColor(if (active || locked) accent else tint)
    }

    private fun panelBackground(lit: Boolean): Drawable {
        val shape = GradientDrawable()
        shape.setColor(container)
        shape.cornerRadius = dp(12).toFloat()
        shape.setStroke(dp(2), if (lit) accent else outline)
        return InsetDrawable(shape, dp(5), dp(1), dp(5), dp(1))
    }

    private fun color(id: Int): Int = ContextCompat.getColor(context, id)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val KEY_HEIGHT_DP = 30
        const val REPEAT_DELAY_MS = 400L
        const val REPEAT_MS = 60L
    }
}
