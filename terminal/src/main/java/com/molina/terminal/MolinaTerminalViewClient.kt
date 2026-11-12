// molina-patch8
package com.molina.terminal

import android.content.Context
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.molina.terminal.emulator.MolinaTerminalSession as ViewSession
import com.molina.terminal.view.TerminalView
import com.molina.terminal.view.TerminalViewClient

/** Klien view: zoom cubit, ketuk memunculkan keyboard. Tombol modifier (Ctrl/Alt) menyusul di Patch berikutnya. */
internal class MolinaTerminalViewClient(
    private val view: TerminalView,
    private val holder: TerminalSessionHolder,
) : TerminalViewClient {

    override fun onScale(scale: Float): Float {
        if (scale in 0.9f..1.1f) return scale
        val step = spToPx(view.context, 1f).coerceAtLeast(1)
        val next = holder.textSizePx + if (scale > 1f) step else -step
        holder.textSizePx = next.coerceIn(spToPx(view.context, MIN_SP), spToPx(view.context, MAX_SP))
        view.setTextSize(holder.textSizePx)
        return 1.0f
    }

    override fun onSingleTapUp(e: MotionEvent?) {
        view.context.getSystemService(InputMethodManager::class.java)
            ?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) = Unit

    override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: ViewSession?): Boolean = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false
    override fun onLongPress(event: MotionEvent?): Boolean = false

    override fun readControlKey(): Boolean = false
    override fun readAltKey(): Boolean = false
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: ViewSession?): Boolean = false
    override fun onEmulatorSet() {
        view.invalidate()
    }

    override fun logError(tag: String?, message: String?) = TerminalLogBridge.e(tag, message)
    override fun logWarn(tag: String?, message: String?) = TerminalLogBridge.w(tag, message)
    override fun logInfo(tag: String?, message: String?) = TerminalLogBridge.i(tag, message)
    override fun logDebug(tag: String?, message: String?) = TerminalLogBridge.d(tag, message)
    override fun logVerbose(tag: String?, message: String?) = Unit
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) = TerminalLogBridge.e(tag, message, e)
    override fun logStackTrace(tag: String?, e: Exception?) = TerminalLogBridge.e(tag, "stack trace", e)

    companion object {
        private const val DEFAULT_SP = 14f
        private const val MIN_SP = 8f
        private const val MAX_SP = 32f

        fun spToPx(context: Context, sp: Float): Int =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics).toInt()

        fun defaultTextSizePx(context: Context): Int = spToPx(context, DEFAULT_SP)
    }
}
