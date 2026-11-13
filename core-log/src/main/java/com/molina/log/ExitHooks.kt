// molina-patch17
package com.molina.log

import kotlin.concurrent.thread

/**
 * Kait pembersihan saat aplikasi keluar total. Modul yang memegang sumber daya native (mis. mpv)
 * mendaftar di sini; AppExit menjalankan semuanya sebelum proses dimatikan.
 */
object ExitHooks {
    private val hooks = LinkedHashMap<String, () -> Unit>()

    fun add(name: String, hook: () -> Unit) {
        synchronized(hooks) { hooks[name] = hook }
    }

    fun remove(name: String) {
        synchronized(hooks) { hooks.remove(name) }
    }

    /**
     * Menjalankan semua kait paralel dan menunggu paling lama [timeoutMs] secara total.
     * Mengembalikan nama kait yang belum selesai pada batas waktu. Kait yang melempar dianggap selesai.
     */
    fun runAll(timeoutMs: Long): List<String> {
        val snapshot = synchronized(hooks) { LinkedHashMap(hooks).also { hooks.clear() } }
        val running = snapshot.map { (name, hook) ->
            name to thread(name = "molina-exit-$name", isDaemon = true) { runCatching(hook) }
        }
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        val late = ArrayList<String>()
        for ((name, t) in running) {
            val leftMs = (deadline - System.nanoTime()) / 1_000_000L
            if (leftMs > 0) t.join(leftMs)
            if (t.isAlive) late.add(name)
        }
        return late
    }
}
