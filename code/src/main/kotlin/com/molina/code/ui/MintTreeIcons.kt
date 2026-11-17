package com.molina.code.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

/**
 * [MINT-TREE] Port Compose dari TreeIconView (:core-ui), gaya Linux Mint (Mint-Y), supaya ikon tree
 * Code identik dengan drawer Terminal. Varian ringkas (tanpa pita ekstensi dan tanpa titik "baru").
 * Folder: map hijau dua lapis (panel belakang + panel depan bergradien, kilau tepi atas, kilat diagonal).
 * Berkas: kertas terang bersudut lipat dengan lambang jenis berwarna gelap.
 */
private val FolderBack = Color(0xFF5F9634)
private val FolderTop = Color(0xFFA9D56F)
private val FolderBottom = Color(0xFF7DB345)
private val Paper = Color(0xFFF5F7F9)
private val PaperFold = Color(0xFFD3DAE1)
private val PaperEdge = Color(0xFF9AA5B1)

internal fun DrawScope.drawMintFolder() {
    val w = size.width
    val h = size.height
    val r = h * 0.1f
    val corner = CornerRadius(r, r)
    // panel belakang dengan tab di kiri atas
    drawRoundRect(FolderBack, Offset(w * 0.02f, h * 0.10f), Size(w * 0.44f, h * 0.32f), corner)
    drawRoundRect(FolderBack, Offset(w * 0.02f, h * 0.22f), Size(w * 0.96f, h * 0.66f), corner)
    // panel depan: gradien hijau muda -> hijau
    val left = w * 0.02f
    val top = h * 0.32f
    val fw = w * 0.96f
    val fh = h * 0.60f
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(FolderTop, FolderBottom), startY = h * 0.32f, endY = h * 0.92f),
        topLeft = Offset(left, top),
        size = Size(fw, fh),
        cornerRadius = corner
    )
    // kilat diagonal lembut di kiri atas panel depan (dipotong mengikuti sudut membulat)
    val clip = Path().apply { addRoundRect(RoundRect(Rect(Offset(left, top), Size(fw, fh)), corner)) }
    clipPath(clip) {
        val sheen = Path().apply {
            moveTo(left, top)
            lineTo(left + fw * 0.55f, top)
            lineTo(left + fw * 0.25f, top + fh)
            lineTo(left, top + fh)
            close()
        }
        drawPath(sheen, Color(0x1FFFFFFF))
    }
    // kilau tipis di tepi atas panel depan
    val sw = 1.dp.toPx()
    drawLine(Color(0xAAFFFFFF), Offset(left + r, top + sw), Offset(left + fw - r, top + sw), strokeWidth = sw)
}

internal fun DrawScope.drawMintFile(kind: MintKind) {
    val w = size.width
    val h = size.height
    val tint = if (kind == MintKind.OTHER) PaperEdge else darken(Color(kind.argb), 0.78f)
    val page = Path().apply {
        moveTo(w * 0.14f, h * 0.06f)
        lineTo(w * 0.60f, h * 0.06f)
        lineTo(w * 0.86f, h * 0.30f)
        lineTo(w * 0.86f, h * 0.94f)
        lineTo(w * 0.14f, h * 0.94f)
        close()
    }
    drawPath(page, Paper)
    val fold = Path().apply {
        moveTo(w * 0.60f, h * 0.06f)
        lineTo(w * 0.60f, h * 0.30f)
        lineTo(w * 0.86f, h * 0.30f)
        close()
    }
    drawPath(fold, PaperFold)
    drawMintGlyph(kind, tint)
    val edge = Stroke(width = 1.2.dp.toPx(), join = StrokeJoin.Round)
    drawPath(page, PaperEdge, style = edge)
    drawPath(fold, PaperEdge, style = edge)
}

private fun darken(c: Color, f: Float) = Color(c.red * f, c.green * f, c.blue * f, 1f)

/** Lambang kecil di badan berkas (di bawah lipatan sudut). */
private fun DrawScope.drawMintGlyph(kind: MintKind, tint: Color) {
    val w = size.width
    val h = size.height
    val top = h * 0.46f
    val bottom = h * 0.82f
    val mid = (top + bottom) / 2f
    val sw = 1.3.dp.toPx()
    val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun ln(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(tint, Offset(x1, y1), Offset(x2, y2), strokeWidth = sw, cap = StrokeCap.Round)
    when (kind) {
        MintKind.SHELL -> {
            val p = Path().apply {
                moveTo(w * 0.3f, top)
                lineTo(w * 0.44f, mid)
                lineTo(w * 0.3f, bottom)
            }
            drawPath(p, tint, style = stroke)
            ln(w * 0.5f, bottom, w * 0.7f, bottom)
        }
        MintKind.CODE, MintKind.MARKUP -> {
            val p = Path().apply {
                moveTo(w * 0.42f, top)
                lineTo(w * 0.3f, mid)
                lineTo(w * 0.42f, bottom)
                moveTo(w * 0.6f, top)
                lineTo(w * 0.72f, mid)
                lineTo(w * 0.6f, bottom)
            }
            drawPath(p, tint, style = stroke)
            if (kind == MintKind.MARKUP) ln(w * 0.54f, top, w * 0.48f, bottom)
        }
        MintKind.DATA -> {
            val p = Path().apply {
                moveTo(w * 0.43f, top)
                lineTo(w * 0.34f, top)
                lineTo(w * 0.34f, bottom)
                lineTo(w * 0.43f, bottom)
                moveTo(w * 0.59f, top)
                lineTo(w * 0.68f, top)
                lineTo(w * 0.68f, bottom)
                lineTo(w * 0.59f, bottom)
            }
            drawPath(p, tint, style = stroke)
        }
        MintKind.IMAGE -> {
            val p = Path().apply {
                moveTo(w * 0.3f, bottom)
                lineTo(w * 0.45f, h * 0.6f)
                lineTo(w * 0.55f, h * 0.72f)
                lineTo(w * 0.62f, h * 0.64f)
                lineTo(w * 0.72f, bottom)
            }
            drawPath(p, tint, style = stroke)
            drawCircle(tint, h * 0.045f, Offset(w * 0.6f, h * 0.52f))
        }
        MintKind.VIDEO -> {
            val p = Path().apply {
                moveTo(w * 0.4f, top)
                lineTo(w * 0.4f, bottom)
                lineTo(w * 0.7f, mid)
                close()
            }
            drawPath(p, tint)
        }
        MintKind.AUDIO -> {
            ln(w * 0.6f, top, w * 0.6f, h * 0.74f)
            ln(w * 0.6f, top, w * 0.72f, h * 0.56f)
            drawCircle(tint, h * 0.07f, Offset(w * 0.52f, h * 0.76f))
        }
        MintKind.SUBTITLE -> {
            ln(w * 0.3f, h * 0.62f, w * 0.72f, h * 0.62f)
            ln(w * 0.3f, h * 0.76f, w * 0.6f, h * 0.76f)
        }
        MintKind.TEXT -> {
            ln(w * 0.3f, h * 0.5f, w * 0.72f, h * 0.5f)
            ln(w * 0.3f, h * 0.64f, w * 0.72f, h * 0.64f)
            ln(w * 0.3f, h * 0.78f, w * 0.55f, h * 0.78f)
        }
        MintKind.ARCHIVE -> {
            drawRect(tint, Offset(w * 0.4f, h * 0.42f), Size(w * 0.12f, h * 0.08f))
            drawRect(tint, Offset(w * 0.5f, h * 0.52f), Size(w * 0.12f, h * 0.08f))
            drawRect(tint, Offset(w * 0.4f, h * 0.62f), Size(w * 0.12f, h * 0.08f))
            drawRect(tint, Offset(w * 0.43f, h * 0.74f), Size(w * 0.14f, h * 0.10f), style = stroke)
        }
        MintKind.OTHER -> Unit
    }
}
