// molina-27n
package com.molina.filemanager

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** [molina-27n] Ikon bento: empat kotak bersudut membulat 2x2, ikon tombol melayang akar. */
internal class BentoGlyphDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val cell = RectF()

    fun setColor(color: Int) {
        if (paint.color == color) return
        paint.color = color
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val side = minOf(b.width(), b.height()) * 0.42f
        val gap = side * 0.16f
        val size = (side - gap) / 2f
        val left = b.exactCenterX() - side / 2f
        val top = b.exactCenterY() - side / 2f
        val radius = size * 0.28f
        for (row in 0..1) {
            for (col in 0..1) {
                val x = left + col * (size + gap)
                val y = top + row * (size + gap)
                cell.set(x, y, x + size, y + size)
                canvas.drawRoundRect(cell, radius, radius, paint)
            }
        }
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
