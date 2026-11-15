package com.molina.filemanager

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.sin

/** [molina-27k] Ikon baris kelompok Lokasi di menu melayang (gaya ikon Windows): Favorit, Internal, Debian, Sampah. */
internal class FlyoutIconDrawable(private val kind: Kind) : Drawable() {
    enum class Kind { STAR, DRIVE, DEBIAN, TRASH }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()

    override fun draw(c: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()).toFloat()
        val ox = b.exactCenterX() - s / 2f
        val oy = b.exactCenterY() - s / 2f
        fun px(u: Float) = ox + u * s
        fun py(u: Float) = oy + u * s
        when (kind) {
            Kind.STAR -> {
                fill.color = 0xFFF2C94C.toInt()
                path.reset()
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) 0.46f else 0.19f
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val x = px(0.5f + r * cos(a).toFloat())
                    val y = py(0.53f + r * sin(a).toFloat())
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                c.drawPath(path, fill)
            }
            Kind.DRIVE -> {
                rect.set(px(0.06f), py(0.30f), px(0.94f), py(0.72f))
                fill.color = 0xFFCBD2DA.toInt()
                c.drawRoundRect(rect, 0.08f * s, 0.08f * s, fill)
                path.reset()
                path.addRoundRect(rect, 0.08f * s, 0.08f * s, Path.Direction.CW)
                c.save()
                c.clipPath(path)
                fill.color = 0xFF6B7682.toInt()
                c.drawRect(px(0.06f), py(0.57f), px(0.94f), py(0.72f), fill)
                c.restore()
                fill.color = 0xFF4CD964.toInt()
                c.drawCircle(px(0.80f), py(0.645f), 0.035f * s, fill)
            }
            Kind.TRASH -> {
                line.strokeWidth = 0.07f * s
                line.color = 0xFFDDE3EA.toInt()
                c.drawLine(px(0.20f), py(0.27f), px(0.80f), py(0.27f), line)
                line.strokeWidth = 0.055f * s
                rect.set(px(0.40f), py(0.14f), px(0.60f), py(0.27f))
                c.drawRoundRect(rect, 0.03f * s, 0.03f * s, line)
                fill.color = 0xFFDDE3EA.toInt()
                path.reset()
                path.moveTo(px(0.25f), py(0.34f))
                path.lineTo(px(0.75f), py(0.34f))
                path.lineTo(px(0.69f), py(0.88f))
                path.lineTo(px(0.31f), py(0.88f))
                path.close()
                c.drawPath(path, fill)
                line.strokeWidth = 0.045f * s
                line.color = 0xFF8A95A1.toInt()
                for (x in floatArrayOf(0.41f, 0.50f, 0.59f)) c.drawLine(px(x), py(0.45f), px(x), py(0.77f), line)
            }
            Kind.DEBIAN -> {
                line.strokeWidth = 0.10f * s
                line.color = 0xFFD70A53.toInt()
                rect.set(px(0.12f), py(0.12f), px(0.88f), py(0.88f))
                c.drawArc(rect, 40f, 300f, false, line)
                line.strokeWidth = 0.085f * s
                rect.set(px(0.30f), py(0.33f), px(0.66f), py(0.69f))
                c.drawArc(rect, 210f, 240f, false, line)
            }
        }
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
