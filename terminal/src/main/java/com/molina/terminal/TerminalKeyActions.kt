// molina-patch9
package com.molina.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.molina.terminal.emulator.KeyHandler

/** Aksi tombol ekstra; semua dipanggil dari main thread. [onScreenChanged] meminta view menggambar ulang. */
internal class TerminalKeyActions(
    private val holder: TerminalSessionHolder,
    private val context: Context,
    private val onScreenChanged: () -> Unit,
) {

    fun send(text: String) {
        holder.session?.write(text)
    }

    fun sendKey(keyCode: Int) {
        val em = holder.session?.getEmulator() ?: return
        KeyHandler.getCode(keyCode, 0, em.isCursorKeysApplicationMode, em.isKeypadApplicationMode)?.let { send(it) }
    }

    /** Tempel ke input shell; bracketed paste ditangani emulator. */
    fun paste() {
        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(context)?.toString()
        if (!text.isNullOrEmpty()) holder.session?.getEmulator()?.paste(text)
    }

    /** Salin seluruh transkrip (scrollback + layar). */
    fun copyAll(): Boolean {
        val text = holder.session?.getEmulator()?.screen?.transcriptText
        if (text.isNullOrEmpty()) return false
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Molina Terminal", text))
        return true
    }

    /** Bersihkan scrollback dan layar, lalu minta shell menggambar ulang prompt. Dilewati bila program layar penuh aktif. */
    fun clear(): Boolean {
        val em = holder.session?.getEmulator() ?: return false
        if (em.isAlternateBufferActive) return false
        val seq = "\u001b[3J\u001b[H\u001b[2J".toByteArray(Charsets.UTF_8)
        em.append(seq, seq.size)
        send("\u000c")
        onScreenChanged()
        return true
    }
}
