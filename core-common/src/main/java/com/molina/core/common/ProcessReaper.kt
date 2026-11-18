package com.molina.core.common

import java.util.concurrent.TimeUnit

/**
 * Menghentikan proses anak dengan tuntas: permintaan berhenti dulu (SIGTERM), lalu paksa (SIGKILL) bila tidak
 * selesai dalam [graceMs]. Mencegah proses menggantung atau zombie di PTY/PRoot. Memblokir paling lama sekitar
 * 2 x graceMs: jangan panggil dari main thread. Untuk proot, `--kill-on-exit` ikut mematikan proses turunannya.
 */
object ProcessReaper {
    /** true bila proses sudah mati saat fungsi kembali. */
    fun terminate(process: Process, graceMs: Long = 1_500L): Boolean {
        if (!process.isAlive) return true
        process.destroy()
        if (waitDead(process, graceMs)) return true
        process.destroyForcibly()
        return waitDead(process, graceMs)
    }

    private fun waitDead(process: Process, ms: Long): Boolean = try {
        process.waitFor(ms, TimeUnit.MILLISECONDS)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        !process.isAlive
    }
}
