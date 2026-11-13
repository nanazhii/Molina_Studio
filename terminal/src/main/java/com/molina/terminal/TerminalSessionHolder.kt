// molina-patch8
package com.molina.terminal

import androidx.lifecycle.ViewModel
import com.molina.terminal.core.PtyTerminalSession
import com.molina.terminal.core.SessionFailure
import com.molina.terminal.view.TerminalView

/**
 * Pemilik sesi terminal, di-scope ke Activity. Pindah tab (fragment diganti) dan rotasi tidak
 * mematikan shell. Sesi yang sudah selesai diganti sesi baru saat tab dibuka lagi.
 */
internal class TerminalSessionHolder : ViewModel() {

    private var finished = false
    private val client = ForwardingSessionClient {
        finished = true
        setFailure(session?.failure)
    }

    var session: PtyTerminalSession? = null
        private set

    /** Ukuran font dalam piksel; 0 berarti belum diatur. Bertahan antar-fragment. */
    var textSizePx: Int = 0

    /** Modifier Ctrl/Alt virtual; bertahan antar-fragment. */
    val modifiers = TerminalModifiers()

    /** Penyebab gagal start proot pada sesi terakhir; null bila tidak ada. */
    var failure: SessionFailure? = null
        private set

    var failureListener: ((SessionFailure?) -> Unit)? = null

    private fun setFailure(value: SessionFailure?) {
        failure = value
        failureListener?.invoke(value)
    }

    fun ensureSession(factory: TerminalSessionFactory): PtyTerminalSession {
        val current = session
        if (current != null && !finished) return current
        current?.close()
        finished = false
        setFailure(null)
        return factory.create(client).also { session = it }
    }

    /** Matikan sesi sekarang dan buat sesi baru (dipakai tombol Mulai ulang sesi). */
    fun restart(factory: TerminalSessionFactory): PtyTerminalSession {
        session?.close()
        finished = false
        setFailure(null)
        return factory.create(client).also { session = it }
    }

    fun bind(view: TerminalView?) {
        client.view = view
    }

    override fun onCleared() {
        client.view = null
        session?.close()
        session = null
    }
}
