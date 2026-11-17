package com.molina.core.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable

/** [molina-27j] Tombol kembali bulat biru dengan panah putih tebal (berubah jadi silang saat mode pilih). */
class BlueBackDrawable : Drawable() {
    var cross: Boolean = false
        set(value) {
            if (field != value) { field = value; invalidateSelf() }
        }

    private var pressed = false
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFDCE8F8.toInt() }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE; strokeCap = Paint.Cap.ROUND }
    private val arrow = Path()

    override fun onBoundsChange(bounds: Rect) {
        fill.shader = LinearGradient(
            0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(),
            0xFF6FB0F2.toInt(), 0xFF1E5CB8.toInt(), Shader.TileMode.CLAMP,
        )
    }

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        val p = state.contains(android.R.attr.state_pressed)
        if (p == pressed) return false
        pressed = p
        invalidateSelf()
        return true
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val r = minOf(b.width(), b.height()) / 2f
        ring.strokeWidth = r * 0.07f
        val rr = r - ring.strokeWidth / 2f
        fill.alpha = if (pressed) 0xB0 else 0xFF
        canvas.drawCircle(cx, cy, rr, fill)
        canvas.drawCircle(cx, cy, rr, ring)
        if (cross) {
            line.strokeWidth = r * 0.16f
            val k = r * 0.30f
            canvas.drawLine(cx - k, cy - k, cx + k, cy + k, line)
            canvas.drawLine(cx - k, cy + k, cx + k, cy - k, line)
        } else {
            val u = r * 0.55f
            arrow.reset()
            arrow.moveTo(cx - 1.00f * u, cy)
            arrow.lineTo(cx - 0.10f * u, cy - 0.85f * u)
            arrow.lineTo(cx - 0.10f * u, cy - 0.32f * u)
            arrow.lineTo(cx + 0.95f * u, cy - 0.32f * u)
            arrow.lineTo(cx + 0.95f * u, cy + 0.32f * u)
            arrow.lineTo(cx - 0.10f * u, cy + 0.32f * u)
            arrow.lineTo(cx - 0.10f * u, cy + 0.85f * u)
            arrow.close()
            canvas.drawPath(arrow, glyph)
        }
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
