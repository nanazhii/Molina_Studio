package com.molina.filemanager

import android.content.Context
import android.view.View
import kotlinx.coroutines.CoroutineScope

/** [molina-27b] Pintu masuk publik: file manager salinan drawer Terminal, mode tertanam (tanpa nano/Jalankan/cd). */
object FileManagerEmbed {
    fun create(context: Context, scope: CoroutineScope): View =
        FileDrawerView(context, FileDrawerState(), scope, NoopHost, embedded = true)

    private object NoopHost : FileDrawerView.Host {
        override fun send(text: String) = Unit
        override fun hasSession() = false
        override fun isProotSession() = false
        override fun isFullScreenProgram() = false
        override fun runInNewSession(line: (proot: Boolean) -> String?) = false
        override fun activateSessionAt(guestDir: String) = false
        override fun showNanoKeys() = Unit
        override fun autoRunNano() = Unit
        override fun runMode() = RunMode.ASK
        override fun rememberRunMode(mode: RunMode) = Unit
        override fun runLogEnabled() = false
        override fun onRootChanged(path: String) = Unit
        override fun onOpenChanged(open: Boolean) = Unit
    }
}
