package com.molina.filemanager

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** [molina-27k] Ikon FAB gabungan: tiga garis hamburger di kiri dan tiga titik vertikal di kanan. */
internal class MenuGlyphDrawable : Drawable() {
    private var barColor = Color.WHITE
    private var dotColor = Color.WHITE
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun setColors(bar: Int, dot: Int) {
        if (bar == barColor && dot == dotColor) return
        barColor = bar
        dotColor = dot
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()).toFloat()
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val gap = 0.15f * s
        val thick = 0.065f * s
        paint.color = barColor
        for (i in -1..1) {
            val y = cy + i * gap
            rect.set(cx - 0.32f * s, y - thick / 2f, cx - 0.02f * s, y + thick / 2f)
            canvas.drawRoundRect(rect, thick / 2f, thick / 2f, paint)
        }
        paint.color = dotColor
        for (i in -1..1) canvas.drawCircle(cx + 0.22f * s, cy + i * gap, 0.05f * s, paint)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
