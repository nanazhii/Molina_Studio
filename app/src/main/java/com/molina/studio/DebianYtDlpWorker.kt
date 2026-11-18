// molina-wave3
package com.molina.studio

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.molina.core.common.ProcessReaper
import com.molina.core.common.SevenZipCommand
import com.molina.core.common.stream.YtDlpProtocol
import com.molina.core.common.stream.YtDlpReply
import com.molina.core.common.stream.YtDlpRunner
import com.molina.terminal.proot.DebianLayout
import com.molina.terminal.proot.ProotCompat
import com.molina.terminal.proot.ProotExec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Worker yt-dlp persisten di Debian PRoot. Satu proses Python (venv `/root/molina-venv`) menerima satu baris per
 * permintaan, sehingga biaya import yt-dlp (beberapa detik) hanya dibayar sekali. Hasil ukur: import ±3,7 dtk,
 * lalu 1,7-4,4 dtk per URL, dibanding ±10 dtk bila yt-dlp dijalankan baru tiap URL.
 *
 * - Permintaan diserialkan (satu per satu). Dibatalkan = balasan basi dilewati lewat id; timeout = proses dimatikan.
 * - Worker dimatikan saat diam [idleMs] atau saat sistem kekurangan memori ([shutdown]); start ulang otomatis.
 * - Pemasangan yt-dlp dilakukan pengguna lewat `~/.molina/pasang_ytdlp.sh` di Terminal. Berkas itu dan skrip worker
 *   ditulis ke rootfs oleh [ensureFiles].
 * - Tanpa shell: argumen dan stdin tidak pernah ditafsirkan. stderr dibuang (bisa memuat URL), URL tidak masuk log.
 */
class DebianYtDlpWorker(
    private val context: Context,
    private val idleMs: Long = IDLE_MS,
) : YtDlpRunner {

    private class Session(
        val process: Process,
        val writer: BufferedWriter,
        val lines: LinkedBlockingQueue<String>,
    ) {
        val alive: Boolean get() = process.isAlive
    }

    private class Unavailable(val code: String, val detail: String) : Exception(detail)

    private val mutex = Mutex()
    private val seq = AtomicLong(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Hanya diakses di dalam [mutex]. */
    private var session: Session? = null
    private var reaper: Job? = null

    @Volatile private var lastUseMs = 0L

    private fun layout() = DebianLayout(context.filesDir)

    private fun lexists(f: File): Boolean = f.exists() || Files.isSymbolicLink(f.toPath())

    override fun isInstalled(): Boolean {
        val venv = File(layout().rootfsDir, "root/molina-venv")
        if (!lexists(File(venv, "bin/python"))) return false
        return File(venv, "lib").listFiles()?.any { File(it, "site-packages/yt_dlp").isDirectory } == true
    }

    /** Tulis skrip worker dan skrip pasang ke rootfs (idempotent). Dipanggil di luar main thread. */
    fun ensureFiles() {
        val l = layout()
        if (!l.isRootfsInstalled()) return
        val dir = File(l.rootfsDir, "root/.molina").apply { mkdirs() }
        writeAsset("molina/ytdlp_worker.py", File(dir, "ytdlp_worker.py"))
        writeAsset("molina/pasang_ytdlp.sh", File(dir, "pasang_ytdlp.sh"))
    }

    private fun writeAsset(name: String, dst: File) {
        val bytes = context.assets.open(name).use { it.readBytes() }
        if (dst.isFile && dst.length() == bytes.size.toLong() && dst.readBytes().contentEquals(bytes)) return
        dst.writeBytes(bytes)
        dst.setReadable(true, false)
    }

    override suspend fun extract(url: String): YtDlpReply = mutex.withLock {
        runInterruptible(Dispatchers.IO) { requestLocked(url) }
    }

    /** Matikan worker (mis. saat memori menipis). Start berikutnya membayar biaya import lagi. */
    fun shutdown() {
        scope.launch { mutex.withLock { closeSession() } }
    }

    // --- Di bawah ini hanya dipanggil sambil memegang mutex, di thread IO ---

    private fun requestLocked(url: String): YtDlpReply {
        var retried = false
        while (true) {
            val reused = session?.alive == true
            val s = try {
                session?.takeIf { it.alive } ?: startSession()
            } catch (e: Unavailable) {
                Log.w(TAG, "worker tidak tersedia: ${e.code}")
                return YtDlpReply.Err(e.code, e.detail)
            }
            val reply = try {
                exchange(s, seq.incrementAndGet(), url)
            } catch (e: IllegalArgumentException) {
                return YtDlpReply.Err("UNSUPPORTED", "URL tidak valid")
            }
            lastUseMs = SystemClock.elapsedRealtime()
            if (reply != null) return reply
            // Worker mati sebelum membalas (mis. dimatikan sistem).
            closeSession()
            if (!reused || retried) return YtDlpReply.Err("UNKNOWN", "worker berhenti")
            retried = true
        }
    }

    /** Satu pertukaran. null = worker mati sebelum membalas. Timeout mematikan worker dan mengembalikan Err(TIMEOUT). */
    private fun exchange(s: Session, id: Long, url: String): YtDlpReply? {
        val line = YtDlpProtocol.request(id, url)
        try {
            s.writer.write(line)
            s.writer.write("\n")
            s.writer.flush()
        } catch (e: IOException) {
            return null
        }
        val deadline = SystemClock.elapsedRealtime() + REQUEST_TIMEOUT_MS
        while (true) {
            val left = deadline - SystemClock.elapsedRealtime()
            val raw = if (left > 0) s.lines.poll(left, TimeUnit.MILLISECONDS) else null
            if (raw == null) {
                Log.w(TAG, "timeout permintaan; worker dimatikan")
                closeSession()
                return YtDlpReply.Err("TIMEOUT", "yt-dlp tidak menjawab")
            }
            if (raw == EOF) return null
            val m = YtDlpProtocol.parse(raw)
            if (m is YtDlpProtocol.Message.Reply && m.id == id) return m.reply
            // Balasan basi (permintaan sebelumnya dibatalkan) atau derau: lewati.
        }
    }

    private fun startSession(): Session {
        if (!isInstalled()) throw Unavailable("NOT_INSTALLED", "yt-dlp belum terpasang")
        val l = layout()
        if (!l.isProotReady()) throw Unavailable("NOT_INSTALLED", "proot belum siap")
        runCatching { ensureFiles() }

        val args = SevenZipCommand.prootArgsScoped(l.rootfsDir.path, emptyList(), PYTHON, listOf(SCRIPT))
        val pb = ProcessBuilder(listOf(l.prootBin.path) + args)
            .directory(context.filesDir)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
        pb.environment().apply {
            clear()
            putAll(
                ProotExec.env(
                    l, context.filesDir.absolutePath,
                    noSeccomp = Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1, compat = ProotCompat.enabled,
                ),
            )
        }
        val proc = try {
            pb.start()
        } catch (e: IOException) {
            throw Unavailable("UNKNOWN", "gagal memulai worker")
        }
        val lines = LinkedBlockingQueue<String>()
        Thread({
            try {
                proc.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { lines.put(it) }
            } catch (e: IOException) {
                // proses selesai atau dimatikan
            } finally {
                lines.offer(EOF)
            }
        }, "molina-ytdlp-reader").apply {
            isDaemon = true
            start()
        }
        val s = Session(proc, proc.outputStream.bufferedWriter(Charsets.UTF_8), lines)

        try {
            val deadline = SystemClock.elapsedRealtime() + READY_TIMEOUT_MS
            while (true) {
                val left = deadline - SystemClock.elapsedRealtime()
                val raw = if (left > 0) lines.poll(left, TimeUnit.MILLISECONDS) else null
                if (raw == null) throw Unavailable("TIMEOUT", "worker tidak siap")
                if (raw == EOF) throw Unavailable("UNKNOWN", "worker berhenti saat start")
                when (val m = YtDlpProtocol.parse(raw)) {
                    is YtDlpProtocol.Message.Ready -> {
                        session = s
                        lastUseMs = SystemClock.elapsedRealtime()
                        startReaper()
                        Log.d(TAG, "worker siap")
                        return s
                    }
                    is YtDlpProtocol.Message.Reply -> {
                        val r = m.reply
                        if (r is YtDlpReply.Err && r.code == "NOT_INSTALLED") {
                            throw Unavailable("NOT_INSTALLED", "yt-dlp tidak bisa diimpor")
                        }
                    }
                    null -> Unit
                }
            }
        } catch (e: Throwable) {
            ProcessReaper.terminate(proc, KILL_GRACE_MS)
            throw e
        }
    }

    private fun closeSession() {
        val s = session ?: return
        session = null
        runCatching {
            s.writer.write(YtDlpProtocol.QUIT + "\n")
            s.writer.flush()
        }
        runCatching { s.writer.close() }
        ProcessReaper.terminate(s.process, KILL_GRACE_MS)
        Log.d(TAG, "worker dihentikan")
    }

    /** Pengawas diam: mematikan worker setelah [idleMs] tanpa permintaan. Berhenti sendiri bila tidak ada sesi. */
    private fun startReaper() {
        if (reaper != null) return
        reaper = scope.launch {
            while (true) {
                delay(REAPER_TICK_MS)
                val stop = mutex.withLock {
                    val s = session
                    if (s != null && (!s.alive || SystemClock.elapsedRealtime() - lastUseMs >= idleMs)) closeSession()
                    if (session == null) {
                        reaper = null
                        true
                    } else {
                        false
                    }
                }
                if (stop) break
            }
        }
    }

    private companion object {
        const val TAG = "Molina/ytdlp"
        const val PYTHON = "/root/molina-venv/bin/python"
        const val SCRIPT = "/root/.molina/ytdlp_worker.py"
        const val EOF = "\u0000EOF"
        const val READY_TIMEOUT_MS = 30_000L
        const val REQUEST_TIMEOUT_MS = 45_000L
        const val IDLE_MS = 5 * 60_000L
        const val REAPER_TICK_MS = 30_000L
        const val KILL_GRACE_MS = 800L
    }
}
