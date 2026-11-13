// molina-patch8
package com.molina.terminal

import android.content.ClipData
import android.content.ClipboardManager
import com.molina.terminal.emulator.TerminalOutput
import com.molina.terminal.emulator.TerminalSessionClient
import com.molina.terminal.view.TerminalView

/**
 * Klien sesi yang hidup selama sesi (di ViewModel), sedangkan [view] berganti tiap fragment
 * dibuat ulang. Hanya diakses dari main thread (PtyTerminalSession memanggil lewat main executor).
 */
internal class ForwardingSessionClient(
    private val onFinished: () -> Unit,
    private val onTitle: () -> Unit = {},
    private val onScreen: () -> Unit = {},
) : TerminalSessionClient {

    var view: TerminalView? = null

    override fun onTextChanged(changedSession: TerminalOutput) {
        view?.onScreenUpdated()
        onScreen()
    }

    override fun onTitleChanged(changedSession: TerminalOutput) = onTitle()

    override fun onSessionFinished(finishedSession: TerminalOutput) = onFinished()

    override fun onCopyTextToClipboard(session: TerminalOutput, text: String?) {
        val v = view ?: return
        if (text.isNullOrEmpty()) return
        v.context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Molina Terminal", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalOutput?) {
        val v = view ?: return
        val clip = v.context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(v.context)?.toString()
        if (!text.isNullOrEmpty()) v.currentSession?.emulator?.paste(text)
    }

    override fun onBell(session: TerminalOutput) = Unit

    override fun onColorsChanged(session: TerminalOutput) {
        view?.invalidate()
    }

    override fun onTerminalCursorStateChange(state: Boolean) = Unit

    override fun setTerminalShellPid(session: TerminalOutput, pid: Int) {
        TerminalLogBridge.i("sesi", "shell pid=$pid")
    }

    override fun getTerminalCursorStyle(): Int? = null

    override fun logError(tag: String?, message: String?) = TerminalLogBridge.e(tag, message)
    override fun logWarn(tag: String?, message: String?) = TerminalLogBridge.w(tag, message)
    override fun logInfo(tag: String?, message: String?) = TerminalLogBridge.i(tag, message)
    override fun logDebug(tag: String?, message: String?) = TerminalLogBridge.d(tag, message)
    override fun logVerbose(tag: String?, message: String?) = Unit
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) = TerminalLogBridge.e(tag, message, e)
    override fun logStackTrace(tag: String?, e: Exception?) = TerminalLogBridge.e(tag, "stack trace", e)
}
