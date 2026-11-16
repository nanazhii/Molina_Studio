// molina-27t
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

/**
 * [molina-27t] Ikon garis monokrom untuk item menu aksi (titik tiga): satu gaya, warna mengikuti teks item
 * (putih, atau merah untuk aksi berbahaya). Digambar dengan Canvas, tanpa aset gambar. Ikon lokasi berwarna
 * (Favorit, Internal, Debian, Sampah) tetap memakai [FlyoutIconDrawable].
 */
internal class MenuIconDrawable(private val kind: Kind, private val tint: Int) : Drawable() {
    enum class Kind {
        REFRESH, NEW_DIR, NEW_FILE, RENAME, SELECT, SELECT_ALL, EXIT, COPY, MOVE, PASTE,
        COMPRESS, EXTRACT, VIEW, TEST, SHARE, SORT, SEARCH, FAVORITE, LINK, INFO, TRASH, DELETE_FOREVER,
    }

    companion object {
        /** [molina-27v] Warna ikon per kelompok aksi (selaras warna jenis berkas); aksi berbahaya tetap merah dari pemanggil. */
        fun colorFor(kind: Kind): Int = when (kind) {
            Kind.REFRESH, Kind.SORT, Kind.SEARCH, Kind.VIEW -> 0xFF64B5F6.toInt() // biru
            Kind.NEW_DIR, Kind.NEW_FILE -> 0xFF00BFA5.toInt() // mint Molina
            Kind.RENAME -> 0xFFFFB74D.toInt() // oranye
            Kind.SELECT, Kind.SELECT_ALL -> 0xFFB388FF.toInt() // ungu
            Kind.EXIT, Kind.LINK -> 0xFFB0BEC5.toInt() // abu
            Kind.COPY, Kind.MOVE, Kind.PASTE -> 0xFF4DD0E1.toInt() // sian
            Kind.COMPRESS, Kind.EXTRACT, Kind.TEST -> 0xFFFFAB40.toInt() // jingga arsip
            Kind.SHARE -> 0xFF81C784.toInt() // hijau
            Kind.FAVORITE -> 0xFFF2C94C.toInt() // emas
            Kind.INFO -> 0xFF4FC3F7.toInt() // biru muda
            Kind.TRASH, Kind.DELETE_FOREVER -> 0xFFFF5252.toInt()
        }
    }

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = tint
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = tint
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
        fun ln(x1: Float, y1: Float, x2: Float, y2: Float) = c.drawLine(px(x1), py(y1), px(x2), py(y2), line)
        fun box(l: Float, t: Float, r: Float, bt: Float, rad: Float = 0.08f) {
            rect.set(px(l), py(t), px(r), py(bt))
            c.drawRoundRect(rect, rad * s, rad * s, line)
        }
        fun plusSign(cx: Float, cy: Float, r: Float) {
            ln(cx - r, cy, cx + r, cy)
            ln(cx, cy - r, cx, cy + r)
        }
        fun chk(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            path.reset()
            path.moveTo(px(x1), py(y1))
            path.lineTo(px(x2), py(y2))
            path.lineTo(px(x3), py(y3))
            c.drawPath(path, line)
        }
        fun arrowHead(tipX: Float, tipY: Float, dx: Float, dy: Float) { // dx,dy = arah panah (-1/0/1)
            val k = 0.12f
            chk(tipX - dx * k - dy * k, tipY - dy * k + dx * k, tipX, tipY, tipX - dx * k + dy * k, tipY - dy * k - dx * k)
        }
        line.strokeWidth = 0.075f * s
        when (kind) {
            Kind.REFRESH -> {
                rect.set(px(0.16f), py(0.16f), px(0.84f), py(0.84f))
                c.drawArc(rect, -60f, 290f, false, line)
                val a = Math.toRadians(-60.0)
                val tx = px(0.5f + 0.34f * cos(a).toFloat())
                val ty = py(0.5f + 0.34f * sin(a).toFloat())
                fill.style = Paint.Style.FILL
                path.reset()
                path.moveTo(tx, ty - 0.17f * s)
                path.lineTo(tx + 0.17f * s, ty + 0.02f * s)
                path.lineTo(tx - 0.08f * s, ty + 0.06f * s)
                path.close()
                c.drawPath(path, fill)
            }
            Kind.NEW_DIR -> {
                path.reset()
                path.moveTo(px(0.10f), py(0.28f))
                path.lineTo(px(0.38f), py(0.28f))
                path.lineTo(px(0.46f), py(0.38f))
                path.lineTo(px(0.90f), py(0.38f))
                path.lineTo(px(0.90f), py(0.80f))
                path.lineTo(px(0.10f), py(0.80f))
                path.close()
                c.drawPath(path, line)
                plusSign(0.50f, 0.60f, 0.12f)
            }
            Kind.NEW_FILE -> {
                path.reset()
                path.moveTo(px(0.22f), py(0.10f))
                path.lineTo(px(0.60f), py(0.10f))
                path.lineTo(px(0.80f), py(0.30f))
                path.lineTo(px(0.80f), py(0.90f))
                path.lineTo(px(0.22f), py(0.90f))
                path.close()
                c.drawPath(path, line)
                plusSign(0.51f, 0.60f, 0.12f)
            }
            Kind.RENAME -> {
                path.reset()
                path.moveTo(px(0.16f), py(0.84f))
                path.lineTo(px(0.20f), py(0.62f))
                path.lineTo(px(0.64f), py(0.18f))
                path.lineTo(px(0.82f), py(0.36f))
                path.lineTo(px(0.38f), py(0.80f))
                path.close()
                c.drawPath(path, line)
                ln(0.56f, 0.26f, 0.74f, 0.44f)
            }
            Kind.SELECT -> {
                box(0.14f, 0.14f, 0.86f, 0.86f, 0.12f)
                chk(0.30f, 0.52f, 0.44f, 0.66f, 0.70f, 0.36f)
            }
            Kind.SELECT_ALL -> {
                box(0.08f, 0.08f, 0.66f, 0.66f, 0.10f)
                box(0.34f, 0.34f, 0.92f, 0.92f, 0.10f)
                chk(0.48f, 0.64f, 0.58f, 0.74f, 0.78f, 0.52f)
            }
            Kind.EXIT -> {
                ln(0.24f, 0.24f, 0.76f, 0.76f)
                ln(0.76f, 0.24f, 0.24f, 0.76f)
            }
            Kind.COPY -> {
                box(0.34f, 0.34f, 0.88f, 0.88f)
                path.reset()
                path.moveTo(px(0.26f), py(0.66f))
                path.lineTo(px(0.12f), py(0.66f))
                path.lineTo(px(0.12f), py(0.12f))
                path.lineTo(px(0.66f), py(0.12f))
                path.lineTo(px(0.66f), py(0.26f))
                c.drawPath(path, line)
            }
            Kind.MOVE -> {
                ln(0.12f, 0.50f, 0.84f, 0.50f)
                arrowHead(0.88f, 0.50f, 1f, 0f)
                ln(0.12f, 0.28f, 0.12f, 0.72f)
            }
            Kind.PASTE -> {
                box(0.16f, 0.20f, 0.84f, 0.90f, 0.10f)
                box(0.34f, 0.08f, 0.66f, 0.28f, 0.06f)
                ln(0.32f, 0.54f, 0.68f, 0.54f)
                ln(0.32f, 0.72f, 0.58f, 0.72f)
            }
            Kind.COMPRESS -> {
                box(0.14f, 0.50f, 0.86f, 0.90f)
                ln(0.50f, 0.08f, 0.50f, 0.52f)
                arrowHead(0.50f, 0.56f, 0f, 1f)
                ln(0.30f, 0.70f, 0.70f, 0.70f)
            }
            Kind.EXTRACT -> {
                box(0.14f, 0.50f, 0.86f, 0.90f)
                ln(0.50f, 0.54f, 0.50f, 0.10f)
                arrowHead(0.50f, 0.06f, 0f, -1f)
                ln(0.30f, 0.70f, 0.70f, 0.70f)
            }
            Kind.VIEW -> {
                path.reset()
                path.moveTo(px(0.06f), py(0.50f))
                path.quadTo(px(0.50f), py(0.10f), px(0.94f), py(0.50f))
                path.quadTo(px(0.50f), py(0.90f), px(0.06f), py(0.50f))
                c.drawPath(path, line)
                c.drawCircle(px(0.50f), py(0.50f), 0.12f * s, fill)
            }
            Kind.TEST -> {
                c.drawCircle(px(0.50f), py(0.50f), 0.38f * s, line)
                chk(0.32f, 0.52f, 0.45f, 0.65f, 0.69f, 0.38f)
            }
            Kind.SHARE -> {
                ln(0.28f, 0.50f, 0.72f, 0.26f)
                ln(0.28f, 0.50f, 0.72f, 0.74f)
                for ((x, y) in listOf(0.24f to 0.50f, 0.76f to 0.24f, 0.76f to 0.76f)) c.drawCircle(px(x), py(y), 0.10f * s, fill)
            }
            Kind.SORT -> {
                ln(0.14f, 0.26f, 0.86f, 0.26f)
                ln(0.14f, 0.50f, 0.66f, 0.50f)
                ln(0.14f, 0.74f, 0.46f, 0.74f)
            }
            Kind.SEARCH -> {
                c.drawCircle(px(0.43f), py(0.43f), 0.28f * s, line)
                ln(0.64f, 0.64f, 0.88f, 0.88f)
            }
            Kind.FAVORITE -> {
                path.reset()
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) 0.44f else 0.19f
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val x = px(0.5f + r * cos(a).toFloat())
                    val y = py(0.54f + r * sin(a).toFloat())
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                c.drawPath(path, line)
            }
            Kind.LINK -> {
                box(0.06f, 0.34f, 0.56f, 0.66f, 0.16f)
                box(0.44f, 0.34f, 0.94f, 0.66f, 0.16f)
            }
            Kind.INFO -> {
                c.drawCircle(px(0.50f), py(0.50f), 0.38f * s, line)
                ln(0.50f, 0.46f, 0.50f, 0.70f)
                c.drawCircle(px(0.50f), py(0.31f), 0.04f * s, fill)
            }
            Kind.TRASH, Kind.DELETE_FOREVER -> {
                ln(0.16f, 0.26f, 0.84f, 0.26f)
                ln(0.40f, 0.26f, 0.40f, 0.14f)
                ln(0.60f, 0.26f, 0.60f, 0.14f)
                ln(0.40f, 0.14f, 0.60f, 0.14f)
                path.reset()
                path.moveTo(px(0.24f), py(0.26f))
                path.lineTo(px(0.30f), py(0.88f))
                path.lineTo(px(0.70f), py(0.88f))
                path.lineTo(px(0.76f), py(0.26f))
                c.drawPath(path, line)
                if (kind == Kind.DELETE_FOREVER) {
                    ln(0.40f, 0.44f, 0.60f, 0.70f)
                    ln(0.60f, 0.44f, 0.40f, 0.70f)
                } else {
                    ln(0.50f, 0.42f, 0.50f, 0.74f)
                }
            }
        }
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
