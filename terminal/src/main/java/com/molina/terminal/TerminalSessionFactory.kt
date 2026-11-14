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

/** Penyimpan status lipat/buka toolbar nano lintas peluncuran; diisi oleh `app` (terminal tidak boleh bergantung pada settings). */
interface NanoKeysStateOwner {
    var nanoKeysOpen: Boolean
}

// molina-patch28
/** Mode tombol Jalankan di drawer berkas: pintar (tab dengan folder sama, else tab baru), tanya (dialog), sesi aktif, atau sesi baru. */
enum class RunMode { SMART, ASK, ACTIVE, NEW }

/** Preferensi Jalankan; diisi oleh `app` dari settings (terminal tidak boleh bergantung pada settings). Dibaca tiap aksi, jadi perubahan setelan langsung berlaku. */
interface RunPrefsOwner {
    val runMode: RunMode
    val runLogEnabled: Boolean
}
