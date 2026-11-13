// molina-patch14
package com.molina.terminal

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.molina.core.ui.R as UiR
import com.molina.terminal.core.SessionFailure

/** Spanduk di atas panel tombol: muncul saat proot gagal start, menawarkan mode kompatibilitas. */
internal class CompatPanelView(context: Context, onRetry: () -> Unit) : LinearLayout(context) {
    private val message = TextView(context)
    private val button = Button(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
        val pad = (12 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad / 2, pad, pad / 2)
        setBackgroundColor(ContextCompat.getColor(context, UiR.color.molina_terminal_container))
        message.setTextColor(ContextCompat.getColor(context, UiR.color.molina_on_surface))
        message.textSize = 13f
        addView(message, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        button.setText(R.string.terminal_compat_button)
        button.isAllCaps = false
        button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, UiR.color.molina_terminal_accent))
        button.setTextColor(ContextCompat.getColor(context, UiR.color.molina_on_terminal_accent))
        button.setOnClickListener { onRetry() }
        addView(button, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    fun render(failure: SessionFailure?, compatActive: Boolean) {
        if (failure == null) {
            visibility = GONE
            return
        }
        message.setText(
            when {
                compatActive -> R.string.terminal_compat_msg_still
                failure == SessionFailure.PROOT_ASSERT -> R.string.terminal_compat_msg_assert
                else -> R.string.terminal_compat_msg_early
            },
        )
        button.visibility = if (compatActive) View.GONE else View.VISIBLE
        visibility = VISIBLE
    }
}
