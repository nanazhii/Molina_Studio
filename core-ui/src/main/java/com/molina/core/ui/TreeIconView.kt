// molina-27p
package com.molina.core.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View

/**
 * [molina-27p] Ikon baris pohon BERSAMA (drawer Terminal dan tab Berkas), gaya Linux Mint (Mint-Y).
 * Folder: map hijau dua lapis (panel belakang + panel depan bergradien, kilau tepi atas, kilat diagonal lembut).
 * Berkas: kertas terang bersudut lipat, lambang jenis menurut [FileKind] berwarna gelap, dan (bila [label] diisi)
 * pita ekstensi berwarna di dasar. Digambar manual agar tanpa aset tambahan.
 */
class TreeIconView(context: Context) : View(context) {
    var isDir: Boolean = false
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    var kind: FileKind = FileKind.OTHER
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    /** Teks pita ekstensi (maks 4 huruf, huruf besar); kosong = tanpa pita. */
    var label: String = ""
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    /** [molina-28c] Titik hijau di pojok kanan atas: item baru sejak drawer terakhir dibuka. */
    var badge: Boolean = false
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val clip = Path()
    private var frontShader: LinearGradient? = null

    init {
        fill.style = Paint.Style.FILL
        line.style = Paint.Style.STROKE
        line.strokeWidth = 1.2f * resources.displayMetrics.density
        line.strokeJoin = Paint.Join.ROUND
        glyph.style = Paint.Style.STROKE
        glyph.strokeWidth = 1.3f * resources.displayMetrics.density
        glyph.strokeJoin = Paint.Join.ROUND
        glyph.strokeCap = Paint.Cap.ROUND
        text.typeface = Typeface.DEFAULT_BOLD
        text.textAlign = Paint.Align.CENTER
        text.color = 0xFF10141A.toInt()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        frontShader = LinearGradient(0f, h * 0.32f, 0f, h * 0.92f, FOLDER_TOP, FOLDER_BOTTOM, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (isDir) drawFolder(canvas, w, h) else drawFile(canvas, w, h)
        if (badge) drawBadge(canvas, w, h) // molina-28c
    }

    private fun drawFolder(canvas: Canvas, w: Float, h: Float) {
        val r = h * 0.1f
        // panel belakang (hijau tua) dengan tab di kiri atas
        fill.color = FOLDER_BACK
        rect.set(w * 0.02f, h * 0.10f, w * 0.46f, h * 0.42f)
        canvas.drawRoundRect(rect, r, r, fill)
        rect.set(w * 0.02f, h * 0.22f, w * 0.98f, h * 0.88f)
        canvas.drawRoundRect(rect, r, r, fill)
        // panel depan: gradien hijau muda -> hijau
        rect.set(w * 0.02f, h * 0.32f, w * 0.98f, h * 0.92f)
        fill.shader = frontShader
        canvas.drawRoundRect(rect, r, r, fill)
        fill.shader = null
        // kilat diagonal lembut di kiri atas panel depan (dipotong mengikuti sudut membulat)
        clip.reset()
        clip.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        path.reset()
        path.moveTo(rect.left, rect.top)
        path.lineTo(rect.left + rect.width() * 0.55f, rect.top)
        path.lineTo(rect.left + rect.width() * 0.25f, rect.bottom)
        path.lineTo(rect.left, rect.bottom)
        path.close()
        fill.color = 0x1FFFFFFF
        canvas.drawPath(path, fill)
        canvas.restore()
        // kilau tipis di tepi atas panel depan
        val sw = 1f * resources.displayMetrics.density
        line.strokeWidth = sw
        line.color = 0xAAFFFFFF.toInt()
        canvas.drawLine(rect.left + r, rect.top + sw, rect.right - r, rect.top + sw, line)
        line.strokeWidth = 1.2f * resources.displayMetrics.density
    }

    private fun drawFile(canvas: Canvas, w: Float, h: Float) {
        val tint = if (kind == FileKind.OTHER) PAPER_EDGE else darken(kind.color, 0.78f)
        // kertas
        path.reset()
        path.moveTo(w * 0.14f, h * 0.06f)
        path.lineTo(w * 0.60f, h * 0.06f)
        path.lineTo(w * 0.86f, h * 0.30f)
        path.lineTo(w * 0.86f, h * 0.94f)
        path.lineTo(w * 0.14f, h * 0.94f)
        path.close()
        fill.color = PAPER
        canvas.drawPath(path, fill)
        // sudut lipat
        val fold = Path()
        fold.moveTo(w * 0.60f, h * 0.06f)
        fold.lineTo(w * 0.60f, h * 0.30f)
        fold.lineTo(w * 0.86f, h * 0.30f)
        fold.close()
        fill.color = PAPER_FOLD
        canvas.drawPath(fold, fill)
        val hasBand = label.isNotEmpty()
        if (hasBand) drawBand(canvas, w, h)
        // lambang jenis (diperkecil dan digeser naik bila ada pita)
        glyph.color = tint
        fill.color = tint
        if (hasBand) {
            canvas.save()
            canvas.translate(0f, -h * 0.22f)
            canvas.scale(0.7f, 0.7f, w * 0.5f, h * 0.64f)
        }
        drawGlyph(canvas, w, h)
        if (hasBand) canvas.restore()
        // garis tepi kertas
        line.color = PAPER_EDGE
        canvas.drawPath(path, line)
        canvas.drawPath(fold, line)
    }

    /** Pita ekstensi di dasar kertas: latar warna jenis, huruf gelap tebal, diperkecil bila terlalu lebar. */
    /** [molina-28c] Titik hijau bercincin gelap agar tetap terbaca di atas folder hijau maupun kertas terang. */
    private fun drawBadge(canvas: Canvas, w: Float, h: Float) {
        val r = h * 0.17f
        val cx = w - r - w * 0.02f
        val cy = r + h * 0.02f
        fill.shader = null
        fill.color = 0xFF000000.toInt()
        canvas.drawCircle(cx, cy, r + 1.2f * resources.displayMetrics.density, fill)
        fill.color = BADGE_GREEN
        canvas.drawCircle(cx, cy, r, fill)
    }

    private fun drawBand(canvas: Canvas, w: Float, h: Float) {
        fill.color = if (kind == FileKind.OTHER) 0xFF9AA0A8.toInt() else kind.color
        rect.set(w * 0.14f, h * 0.62f, w * 0.86f, h * 0.94f)
        canvas.drawRect(rect, fill)
        text.textSize = h * 0.27f
        val maxW = w * 0.64f
        val mw = text.measureText(label)
        if (mw > maxW) text.textSize = text.textSize * maxW / mw
        val fm = text.fontMetrics
        canvas.drawText(label, w * 0.5f, rect.centerY() - (fm.ascent + fm.descent) / 2f, text)
    }

    private fun darken(c: Int, f: Float): Int {
        val r = ((c shr 16) and 0xFF) * f
        val g = ((c shr 8) and 0xFF) * f
        val b = (c and 0xFF) * f
        return (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }

    private companion object {
        val FOLDER_BACK = 0xFF5F9634.toInt()
        val FOLDER_TOP = 0xFFA9D56F.toInt()
        val BADGE_GREEN = 0xFF00E676.toInt() // molina-28c
        val FOLDER_BOTTOM = 0xFF7DB345.toInt()
        val PAPER = 0xFFF5F7F9.toInt()
        val PAPER_FOLD = 0xFFD3DAE1.toInt()
        val PAPER_EDGE = 0xFF9AA5B1.toInt()
    }

    /** Lambang kecil di badan berkas (di bawah lipatan sudut). */
    private fun drawGlyph(canvas: Canvas, w: Float, h: Float) {
        val top = h * 0.46f
        val bottom = h * 0.82f
        val mid = (top + bottom) / 2f
        when (kind) {
            FileKind.SHELL -> {
                path.reset()
                path.moveTo(w * 0.3f, top)
                path.lineTo(w * 0.44f, mid)
                path.lineTo(w * 0.3f, bottom)
                canvas.drawPath(path, glyph)
                canvas.drawLine(w * 0.5f, bottom, w * 0.7f, bottom, glyph)
            }
            FileKind.CODE, FileKind.MARKUP -> {
                path.reset()
                path.moveTo(w * 0.42f, top)
                path.lineTo(w * 0.3f, mid)
                path.lineTo(w * 0.42f, bottom)
                path.moveTo(w * 0.6f, top)
                path.lineTo(w * 0.72f, mid)
                path.lineTo(w * 0.6f, bottom)
                canvas.drawPath(path, glyph)
                if (kind == FileKind.MARKUP) canvas.drawLine(w * 0.54f, top, w * 0.48f, bottom, glyph)
            }
            FileKind.DATA -> {
                path.reset()
                path.moveTo(w * 0.43f, top)
                path.lineTo(w * 0.34f, top)
                path.lineTo(w * 0.34f, bottom)
                path.lineTo(w * 0.43f, bottom)
                path.moveTo(w * 0.59f, top)
                path.lineTo(w * 0.68f, top)
                path.lineTo(w * 0.68f, bottom)
                path.lineTo(w * 0.59f, bottom)
                canvas.drawPath(path, glyph)
            }
            FileKind.IMAGE -> {
                path.reset()
                path.moveTo(w * 0.3f, bottom)
                path.lineTo(w * 0.45f, h * 0.6f)
                path.lineTo(w * 0.55f, h * 0.72f)
                path.lineTo(w * 0.62f, h * 0.64f)
                path.lineTo(w * 0.72f, bottom)
                canvas.drawPath(path, glyph)
                canvas.drawCircle(w * 0.6f, h * 0.52f, h * 0.045f, fill)
            }
            FileKind.VIDEO -> {
                path.reset()
                path.moveTo(w * 0.4f, top)
                path.lineTo(w * 0.4f, bottom)
                path.lineTo(w * 0.7f, mid)
                path.close()
                canvas.drawPath(path, fill)
            }
            FileKind.AUDIO -> {
                canvas.drawLine(w * 0.6f, top, w * 0.6f, h * 0.74f, glyph)
                canvas.drawLine(w * 0.6f, top, w * 0.72f, h * 0.56f, glyph)
                canvas.drawCircle(w * 0.52f, h * 0.76f, h * 0.07f, fill)
            }
            FileKind.SUBTITLE -> {
                canvas.drawLine(w * 0.3f, h * 0.62f, w * 0.72f, h * 0.62f, glyph)
                canvas.drawLine(w * 0.3f, h * 0.76f, w * 0.6f, h * 0.76f, glyph)
            }
            FileKind.TEXT -> {
                canvas.drawLine(w * 0.3f, h * 0.5f, w * 0.72f, h * 0.5f, glyph)
                canvas.drawLine(w * 0.3f, h * 0.64f, w * 0.72f, h * 0.64f, glyph)
                canvas.drawLine(w * 0.3f, h * 0.78f, w * 0.55f, h * 0.78f, glyph)
            }
            FileKind.ARCHIVE -> {
                rect.set(w * 0.4f, h * 0.42f, w * 0.52f, h * 0.5f)
                canvas.drawRect(rect, fill)
                rect.set(w * 0.5f, h * 0.52f, w * 0.62f, h * 0.6f)
                canvas.drawRect(rect, fill)
                rect.set(w * 0.4f, h * 0.62f, w * 0.52f, h * 0.7f)
                canvas.drawRect(rect, fill)
                rect.set(w * 0.43f, h * 0.74f, w * 0.57f, h * 0.84f)
                canvas.drawRect(rect, glyph)
            }
            else -> Unit
        }
    }
}
