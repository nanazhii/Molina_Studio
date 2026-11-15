package com.molina.filemanager

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.molina.core.ui.R as UiR

/** [molina-27b] Salinan HeaderDotsView milik Terminal: tiga titik vertikal untuk menu header. */
internal class HeaderDotsView(context: Context) : View(context) {
    var statusColor: Int? = null
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val status = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        dot.color = ColorUtils.setAlphaComponent(Color.WHITE, 0xDD)
        isClickable = true
        background = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(ContextCompat.getColor(context, UiR.color.molina_terminal_accent), 0x55)),
            null,
            ColorDrawable(Color.WHITE),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        for (i in -1..1) canvas.drawCircle(cx, cy + i * 7f * d, 2.2f * d, dot)
        statusColor?.let {
            status.color = it
            canvas.drawCircle(cx + 7f * d, cy - 9.5f * d, 3.5f * d, status)
        }
    }
}
