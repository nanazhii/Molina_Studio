// molina-patch8
package com.molina.terminal

import com.molina.terminal.core.PtyTerminalSession
import com.molina.terminal.emulator.TerminalSessionClient

/**
 * Pabrik sesi terminal. Diisi oleh `app` sehingga fitur tidak tahu perintah, direktori kerja,
 * maupun lingkungan proses (nanti: Debian PRoot dari `terminal-proot`).
 */
fun interface TerminalSessionFactory {
    fun create(client: TerminalSessionClient): PtyTerminalSession
}

/** Dipenuhi oleh Activity yang menampung [TerminalFragment]. */
interface TerminalSessionFactoryOwner {
    val terminalSessionFactory: TerminalSessionFactory
}
