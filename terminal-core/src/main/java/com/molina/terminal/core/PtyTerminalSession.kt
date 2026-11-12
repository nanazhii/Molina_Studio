package com.molina.terminal.core

import android.os.Handler
import android.os.Looper
import com.molina.terminal.emulator.TerminalEmulator
import com.molina.terminal.emulator.TerminalOutput
import com.molina.terminal.emulator.TerminalSessionClient
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import com.molina.terminal.emulator.MolinaTerminalSession as ViewSession

/** Menjalankan perintah di main thread lewat [Looper.getMainLooper]. */
object MainThreadExecutor : Executor {
    private val handler by lazy(LazyThreadSafetyMode.PUBLICATION) { Handler(Looper.getMainLooper()) }
    override fun execute(command: Runnable) {
        handler.post(command)
    }
}

/**
 * Sesi terminal berbasis PTY untuk `terminal-view`.
 *
 * - Emulator hanya disentuh dari [mainExecutor] (main thread di aplikasi).
 * - Pembacaan keluaran dan `waitFor` memakai thread khusus karena pemanggilan blokir native tidak
 *   bisa dibatalkan; penulisan ke proses lewat antrean dan thread sendiri agar main thread tidak
 *   pernah menulis ke PTY.
 * - Keluaran diteruskan sebagai byte mentah; dekode UTF-8 dilakukan emulator, jadi karakter
 *   multibyte yang terpotong di batas buffer aman.
 * - Proses dijalankan saat [updateSize] pertama dipanggil (view baru tahu ukurannya di sana).
 * - [close] aman dipanggil berulang dan dari mana saja; sesudahnya tidak ada callback ke [client].
 */
class PtyTerminalSession(
    private val config: SessionConfig,
    private val client: TerminalSessionClient,
    private val mainExecutor: Executor = MainThreadExecutor,
    private val launcher: PtyLauncher = DefaultPtyLauncher,
    private val transcriptRows: Int? = null,
) : TerminalOutput(), ViewSession, AutoCloseable {

    private var term: TerminalEmulator? = null // hanya main thread

    @Volatile
    private var channel: PtyChannel? = null
    private val closed = AtomicBoolean(false)
    private val finished = AtomicBoolean(false)
    private val writeQueue = LinkedBlockingQueue<ByteArray>()
    private val pending = Semaphore(MAX_PENDING_CHUNKS)

    /** Kode keluar proses (-sinyal bila dihentikan sinyal); null selama berjalan atau bila tak diketahui. */
    @Volatile
    var exitStatus: Int? = null
        private set

    val isRunning: Boolean get() = channel != null && !finished.get() && !closed.get()

    override fun getEmulator(): TerminalEmulator? = term

    override fun updateSize(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        if (columns < 1 || rows < 1 || closed.get()) return
        val emulator = term
        if (emulator == null) {
            start(columns, rows, cellWidthPixels, cellHeightPixels)
            return
        }
        channel?.resize(rows, columns, cellWidthPixels, cellHeightPixels)
        emulator.resize(columns, rows, cellWidthPixels, cellHeightPixels)
    }

    override fun writeCodePoint(prependEscape: Boolean, codePoint: Int) {
        require(codePoint in 0..0x10FFFF && codePoint !in 0xD800..0xDFFF) { "code point tidak valid: $codePoint" }
        val text = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8)
        val bytes = if (prependEscape) byteArrayOf(ESC) + text else text
        write(bytes, 0, bytes.size)
    }

    override fun reset() {
        term?.reset()
        client.onTextChanged(this)
    }

    override fun write(data: ByteArray, offset: Int, count: Int) {
        if (count <= 0 || closed.get() || finished.get() || channel == null) return
        writeQueue.offer(data.copyOfRange(offset, offset + count))
    }

    override fun titleChanged(oldTitle: String?, newTitle: String?) = client.onTitleChanged(this)

    override fun onCopyTextToClipboard(text: String?) = client.onCopyTextToClipboard(this, text)

    override fun onPasteTextFromClipboard() = client.onPasteTextFromClipboard(this)

    override fun onBell() = client.onBell(this)

    override fun onColorsChanged() = client.onColorsChanged(this)

    /** Hentikan proses (SIGKILL) dan lepaskan sumber daya. Tidak ada callback ke client sesudahnya. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        writeQueue.offer(STOP)
        channel?.let {
            it.kill()
            it.close()
        }
    }

    private fun start(columns: Int, rows: Int, cellWidth: Int, cellHeight: Int) {
        val emulator = TerminalEmulator(this, columns, rows, cellWidth, cellHeight, transcriptRows, client)
        term = emulator
        val failure: Throwable? = try {
            channel = launcher.launch(config, rows, columns, cellWidth, cellHeight)
            null
        } catch (e: Exception) {
            e
        } catch (e: LinkageError) {
            e // pustaka native tidak termuat
        }
        val chan = channel
        if (failure != null || chan == null) {
            emulator.appendText("[Gagal memulai proses: ${failure?.message ?: "tidak diketahui"}]\r\n")
            client.onTextChanged(this)
            finish(null)
            return
        }
        client.setTerminalShellPid(this, chan.pid)
        thread(name = "molina-pty-read", isDaemon = true) { readLoop(chan) }
        thread(name = "molina-pty-write", isDaemon = true) { writeLoop(chan) }
    }

    private fun readLoop(chan: PtyChannel) {
        val buffer = ByteArray(READ_BUFFER_SIZE)
        try {
            while (true) {
                val n = chan.input.read(buffer)
                if (n < 0) break
                if (n == 0) continue
                if (!acquirePermit()) break
                val chunk = buffer.copyOf(n)
                mainExecutor.execute { deliver(chunk) }
            }
        } catch (_: IOException) {
            // EIO saat proses selesai, atau master ditutup oleh close()
        }
        val code: Int? = try {
            chan.waitFor()
        } catch (_: Exception) {
            null
        }
        writeQueue.offer(STOP)
        chan.close()
        mainExecutor.execute { finish(code) }
    }

    private fun writeLoop(chan: PtyChannel) {
        val out = chan.output
        try {
            while (true) {
                val data = writeQueue.take()
                if (data === STOP) break
                out.write(data)
                out.flush()
            }
        } catch (_: IOException) {
            // proses sudah selesai
        }
    }

    /** Menunggu slot antrean main thread (backpressure). False bila sesi ditutup. */
    private fun acquirePermit(): Boolean {
        while (!closed.get()) {
            if (pending.tryAcquire(PERMIT_POLL_MS, TimeUnit.MILLISECONDS)) return true
        }
        return false
    }

    private fun deliver(chunk: ByteArray) {
        try {
            if (!closed.get()) {
                term?.append(chunk, chunk.size)
                client.onTextChanged(this)
            }
        } finally {
            pending.release()
        }
    }

    private fun finish(code: Int?) {
        exitStatus = code
        if (closed.get() || !finished.compareAndSet(false, true)) return
        val note = if (code == null) "\r\n[Proses selesai]\r\n" else "\r\n[Proses selesai, kode $code]\r\n"
        term?.appendText(note)
        client.onTextChanged(this)
        client.onSessionFinished(this)
    }

    private fun TerminalEmulator.appendText(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        append(bytes, bytes.size)
    }

    private companion object {
        const val READ_BUFFER_SIZE = 64 * 1024
        const val MAX_PENDING_CHUNKS = 32
        const val PERMIT_POLL_MS = 50L
        const val ESC: Byte = 27
        val STOP = ByteArray(0)
    }
}
