// molina-patch18
package com.molina.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.molina.core.ui.R as UiR
import kotlin.math.max

/**
 * Bilah sesi berbentuk pil (desain Molina-Studio): pil aktif berbingkai hijau dengan titik status,
 * garis bawah, dan tombol x (kill + tutup); pil lain redup; tombol + membuat sesi baru.
 */
internal class SessionTabBar(
    context: Context,
    private val onSelect: (Int) -> Unit,
    private val onAdd: () -> Unit,
    private val onClose: (Int) -> Unit,
) : HorizontalScrollView(context) {

    private val brand = color(UiR.color.molina_tab_brand)
    private val dotColor = color(UiR.color.molina_tab_dot)
    private val shellDotColor = color(UiR.color.molina_tab_dot_shell)
    private val closeColor = color(UiR.color.molina_tab_close)
    private val idleBg = color(UiR.color.molina_tab_idle_bg)
    private val line = color(UiR.color.molina_tab_line)
    private val muted = color(UiR.color.molina_tab_muted)

    private val row = LinearLayout(context)
    private var lastKey: List<Any>? = null
    private var lastCurrent = -1
    private var lastCount = -1

    init {
        isFillViewport = true
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        background = LayerDrawable(
            arrayOf(ColorDrawable(color(UiR.color.molina_tab_bar_bg)), ColorDrawable(line)),
        ).apply {
            setLayerGravity(1, Gravity.BOTTOM)
            setLayerHeight(1, dp(1))
        }
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(8), 0, dp(8), dp(1))
        addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    /** Gambar ulang hanya bila isi berubah (judul shell bisa berubah tiap prompt). */
    fun render(titles: List<String>, shells: List<Boolean>, current: Int, canAdd: Boolean) {
        val key = listOf<Any>(titles, shells, current, canAdd)
        if (key == lastKey) return
        lastKey = key
        row.removeAllViews()
        var selected: View? = null
        titles.forEachIndexed { i, title ->
            val pill = Pill(i, title, i == current, shells.getOrElse(i) { false })
            row.addView(pill, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(PILL_DP)).apply { marginEnd = dp(6) })
            if (i == current) selected = pill
        }
        row.addView(addPill(canAdd), LinearLayout.LayoutParams(dp(36), dp(PILL_DP)))
        if (current != lastCurrent || titles.size != lastCount) {
            lastCurrent = current
            lastCount = titles.size
            selected?.let { s -> post { smoothScrollTo(max(0, s.left - dp(24)), 0) } }
        }
    }

    private inner class Pill(
        private val index: Int,
        title: String,
        private val selected: Boolean,
        private val shell: Boolean,
    ) : LinearLayout(this@SessionTabBar.context) {

        private val accent = if (shell) shellDotColor else brand

        private val underline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            strokeWidth = dp(2).toFloat()
            strokeCap = Paint.Cap.ROUND
        }

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setWillNotDraw(false)
            setPadding(dp(10), 0, if (selected) dp(3) else dp(10), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(PILL_DP).toFloat()
                setColor(if (selected) ColorUtils.setAlphaComponent(accent, 0x1F) else idleBg)
                setStroke(dp(1), if (selected) ColorUtils.setAlphaComponent(accent, 0x80) else line)
            }
            if (selected) {
                val dot = View(context)
                dot.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (shell) shellDotColor else dotColor)
                }
                addView(dot, LinearLayout.LayoutParams(dp(6), dp(6)).apply { marginEnd = dp(6) })
            }
            val label = TextView(context).apply {
                text = "${index + 1} $title"
                textSize = 11f
                typeface = if (selected) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.MONOSPACE
                setTextColor(if (selected) Color.WHITE else muted)
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                maxWidth = dp(120)
            }
            addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            if (selected) {
                val close = TextView(context).apply {
                    text = "\u2715"
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(closeColor)
                    contentDescription = resources.getString(R.string.terminal_tab_close_desc)
                    setOnClickListener {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onClose(index)
                    }
                }
                addView(close, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginStart = dp(4) })
            }
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onSelect(index)
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!selected) return
            val y = height - dp(2) - underline.strokeWidth / 2f
            canvas.drawLine(width * 0.2f, y, width * 0.8f, y, underline)
        }
    }

    private fun addPill(enabledLook: Boolean): TextView = TextView(context).apply {
        text = "+"
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(brand)
        alpha = if (enabledLook) 1f else 0.4f
        background = GradientDrawable().apply {
            cornerRadius = dp(PILL_DP).toFloat()
            setStroke(dp(1), ColorUtils.setAlphaComponent(brand, 0x99))
        }
        contentDescription = resources.getString(R.string.terminal_tab_add)
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onAdd()
        }
    }

    private fun color(id: Int): Int = ContextCompat.getColor(context, id)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        const val HEIGHT_DP = 36
        private const val PILL_DP = 28
    }
}
