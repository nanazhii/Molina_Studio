// molina-patch18
package com.molina.terminal

import androidx.lifecycle.ViewModel
import com.molina.log.MolinaEvents
import com.molina.terminal.core.PtyTerminalSession
import com.molina.terminal.core.SessionFailure
import com.molina.terminal.view.TerminalView
import kotlin.math.min

/**
 * Pemilik daftar sesi terminal (maks. [MAX_SESSIONS]), di-scope ke Activity. Pindah tab aplikasi
 * dan rotasi tidak mematikan shell. Hanya sesi aktif yang terhubung ke view; sesi lain tetap
 * berjalan di latar. Semua mutasi terjadi di main thread.
 *
 * Sesi yang berakhir normal (exit) ditutup otomatis kecuali tinggal satu atau gagal start
 * (supaya penyebabnya tetap terbaca di layar dan panel kompatibilitas).
 */
internal class TerminalSessionHolder : ViewModel() {

    private class Entry {
        lateinit var session: PtyTerminalSession
        lateinit var client: ForwardingSessionClient
        var finished = false
    }

    private val entries = ArrayList<Entry>()
    private var boundView: TerminalView? = null

    var currentIndex: Int = 0
        private set

    val count: Int get() = entries.size
    val canAdd: Boolean get() = entries.size < MAX_SESSIONS

    /** Sesi aktif; null hanya sebelum [ensureSession] pertama. */
    val session: PtyTerminalSession? get() = entries.getOrNull(currentIndex)?.session

    /** Penyebab gagal start proot pada sesi aktif; null bila tidak ada. */
    val failure: SessionFailure? get() = session?.failure

    /** Ukuran font dalam piksel; 0 berarti belum diatur. Bertahan antar-fragment. */
    var textSizePx: Int = 0

    /** Modifier Ctrl/Alt virtual; bertahan antar-fragment. */
    val modifiers = TerminalModifiers()

    /** Dipanggil saat daftar, pilihan, judul, atau status sesi berubah. */
    var tabsListener: (() -> Unit)? = null

    /** Judul dari shell (OSC), null bila kosong. */
    fun titleOf(index: Int): String? =
        entries.getOrNull(index)?.session?.getEmulator()?.title?.trim()?.takeIf { it.isNotEmpty() }

    fun ensureSession(factory: TerminalSessionFactory): PtyTerminalSession {
        val current = entries.getOrNull(currentIndex)
        if (current != null && !current.finished) return current.session
        return replaceAt(currentIndex, factory)
    }

    /** Tombol Mulai ulang sesi: matikan sesi aktif dan buat penggantinya di tab yang sama. */
    fun restart(factory: TerminalSessionFactory): PtyTerminalSession = replaceAt(currentIndex, factory)

    /** Tombol +: sesi baru dan langsung aktif. Null bila sudah [MAX_SESSIONS]. */
    fun newSession(factory: TerminalSessionFactory): PtyTerminalSession? {
        pruneFinished()
        if (!canAdd) return null
        val entry = create(factory)
        entries.add(entry)
        currentIndex = entries.lastIndex
        tabEvent("new", currentIndex)
        rebind()
        tabsListener?.invoke()
        return entry.session
    }

    fun select(index: Int) {
        if (index !in entries.indices || index == currentIndex) return
        currentIndex = index
        tabEvent("select", index)
        rebind()
        tabsListener?.invoke()
    }

    /** Tombol x: kill proses sesi (SIGKILL lewat close) lalu tutup tabnya. Bila terakhir, diganti sesi baru. */
    fun closeSession(index: Int, factory: TerminalSessionFactory) {
        if (index !in entries.indices) return
        tabEvent("close", index)
        dropAt(index)
        if (entries.isEmpty()) {
            entries.add(create(factory))
            currentIndex = 0
        }
        rebind()
        tabsListener?.invoke()
    }

    fun bind(view: TerminalView?) {
        boundView = view
        rebind()
    }

    private fun create(factory: TerminalSessionFactory): Entry {
        val entry = Entry()
        entry.client = ForwardingSessionClient(
            onFinished = { onEntryFinished(entry) },
            onTitle = { tabsListener?.invoke() },
        )
        entry.session = factory.create(entry.client)
        return entry
    }

    private fun replaceAt(index: Int, factory: TerminalSessionFactory): PtyTerminalSession {
        val fresh = create(factory)
        if (index in entries.indices) {
            entries[index].client.view = null
            entries[index].session.close()
            entries[index] = fresh
            currentIndex = index
        } else {
            entries.add(fresh)
            currentIndex = entries.lastIndex
        }
        rebind()
        tabsListener?.invoke()
        return fresh.session
    }

    private fun onEntryFinished(entry: Entry) {
        val index = entries.indexOf(entry)
        if (index < 0) return
        entry.finished = true
        if (entry.session.failure == null && entries.size > 1) {
            tabEvent("auto-close", index)
            dropAt(index)
        }
        rebind()
        tabsListener?.invoke()
    }

    private fun pruneFinished() {
        var i = entries.lastIndex
        while (i >= 0) {
            val e = entries[i]
            if (e.finished && e.session.failure == null) dropAt(i)
            i--
        }
    }

    private fun dropAt(index: Int) {
        val entry = entries.removeAt(index)
        entry.client.view = null
        entry.session.close()
        currentIndex = when {
            entries.isEmpty() -> 0
            index < currentIndex -> currentIndex - 1
            index == currentIndex -> min(index, entries.lastIndex)
            else -> currentIndex
        }
    }

    /** Hanya sesi aktif yang menggambar ke view. */
    private fun rebind() {
        entries.forEachIndexed { i, e -> e.client.view = if (i == currentIndex) boundView else null }
    }

    private fun tabEvent(action: String, index: Int) = MolinaEvents.behavior(
        "term", "tab",
        fields = mapOf("action" to action, "index" to (index + 1).toString(), "count" to entries.size.toString()),
    )

    override fun onCleared() {
        entries.forEach {
            it.client.view = null
            it.session.close()
        }
        entries.clear()
        boundView = null
    }

    companion object {
        const val MAX_SESSIONS = 5
    }
}
