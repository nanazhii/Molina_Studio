// molina-ytdlp-install
package com.molina.studio

import android.content.Context
import android.os.SystemClock
import com.molina.core.common.ProcessReaper
import com.molina.core.common.diag.Diag
import com.molina.core.common.stream.YtDlpInstallProtocol
import com.molina.core.common.stream.YtDlpInstallResult
import com.molina.core.common.stream.YtDlpInstallStage
import com.molina.core.common.stream.YtDlpInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pemasang yt-dlp di Debian PRoot: menjalankan `~/.molina/pasang_ytdlp.sh` di latar belakang dan membaca baris
 * MOLINA_* dari stdout (lihat [YtDlpInstallProtocol]).
 *
 * Hemat memori: tidak ada pip/venv/kompilator; skrip hanya memasang python3 + ca-certificates tanpa paket
 * rekomendasi dan mengunduh satu berkas (±3 MB) secara streaming. Sisi aplikasi hanya membaca baris pendek, dan
 * keluaran apt/unduhan dibuang di skrip. Tanpa shell di sisi aplikasi; stderr dibuang; URL tidak pernah dicatat.
 */
class DebianYtDlpInstaller(
    private val context: Context,
    workerProvider: () -> DebianYtDlpWorker,
) : YtDlpInstaller {
    private val worker by lazy(workerProvider)
    private val running = AtomicBoolean(false)

    override fun isInstalled(): Boolean = worker.isInstalled()

    override suspend fun install(onStage: (YtDlpInstallStage) -> Unit): YtDlpInstallResult {
        if (!running.compareAndSet(false, true)) return YtDlpInstallResult.Failed("BUSY")
        val t0 = SystemClock.elapsedRealtime()
        try {
            Diag.behavior("ytdlp", "install-start")
            val result = withContext(Dispatchers.IO) { runScript(onStage) }
            val ms = (SystemClock.elapsedRealtime() - t0).toString()
            when (result) {
                is YtDlpInstallResult.Done ->
                    Diag.behavior("ytdlp", "install-done", fields = mapOf("version" to result.version, "ms" to ms))
                is YtDlpInstallResult.Failed ->
                    Diag.bug("ytdlp", "install-failed", fields = mapOf("code" to result.code, "ms" to ms))
            }
            return result
        } finally {
            running.set(false)
        }
    }

    private suspend fun runScript(onStage: (YtDlpInstallStage) -> Unit): YtDlpInstallResult {
        // Rootfs baru/di-reset tidak punya ~/.molina: tulis ulang skrip sebelum dijalankan.
        if (!runCatching { worker.ensureFiles() }.getOrDefault(false)) return YtDlpInstallResult.Failed("NO_ROOTFS")
        val pb = DebianExec.builder(context, "/bin/sh", listOf(SCRIPT)) ?: return YtDlpInstallResult.Failed("NO_ROOTFS")
        val proc = try {
            pb.start()
        } catch (e: IOException) {
            return YtDlpInstallResult.Failed("FAILED")
        }
        val timedOut = AtomicBoolean(false)
        val finished = AtomicBoolean(false)
        try {
            return coroutineScope {
                val watchdog = launch(Dispatchers.Default) {
                    delay(TIMEOUT_MS)
                    timedOut.set(true)
                    proc.destroy()
                }
                // Dibatalkan dari luar -> matikan proses (non-blocking) supaya pembacaan di bawah berhenti.
                val cancelWatcher = launch(Dispatchers.Default) {
                    try {
                        awaitCancellation()
                    } finally {
                        if (!finished.get()) proc.destroy()
                    }
                }
                try {
                    readOutcome(proc, onStage, timedOut)
                } finally {
                    finished.set(true)
                    watchdog.cancel()
                    cancelWatcher.cancel()
                }
            }
        } finally {
            ProcessReaper.terminate(proc, KILL_GRACE_MS)
        }
    }

    /** Membaca baris protokol sampai proses selesai. Satu baris dalam memori pada satu waktu. */
    private fun readOutcome(
        proc: Process,
        onStage: (YtDlpInstallStage) -> Unit,
        timedOut: AtomicBoolean,
    ): YtDlpInstallResult {
        var done: String? = null
        var fail: String? = null
        try {
            proc.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    when (val e = YtDlpInstallProtocol.parse(line)) {
                        is YtDlpInstallProtocol.Event.Stage -> onStage(e.stage)
                        is YtDlpInstallProtocol.Event.Done -> done = e.version
                        is YtDlpInstallProtocol.Event.Fail -> fail = e.code
                        null -> Unit
                    }
                }
            }
        } catch (e: IOException) {
            // proses dimatikan (dibatalkan atau timeout)
        }
        if (timedOut.get()) return YtDlpInstallResult.Failed("TIMEOUT")
        done?.let { return YtDlpInstallResult.Done(it) }
        return YtDlpInstallResult.Failed(fail ?: "FAILED")
    }

    private companion object {
        const val SCRIPT = "/root/.molina/pasang_ytdlp.sh"
        const val TIMEOUT_MS = 10 * 60_000L
        const val KILL_GRACE_MS = 800L
    }
}
