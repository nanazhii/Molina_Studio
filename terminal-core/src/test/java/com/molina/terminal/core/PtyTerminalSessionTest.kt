package com.molina.terminal.core

import com.molina.terminal.emulator.TerminalOutput
import com.molina.terminal.emulator.TerminalSessionClient
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PtyTerminalSessionTest {

    private class QueueInputStream : InputStream() {
        private val queue = LinkedBlockingQueue<ByteArray>()
        private var current = ByteArray(0)
        private var pos = 0
        private var eof = false

        fun feed(data: ByteArray) = queue.put(data)
        fun end() = queue.put(EOF)

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            while (pos >= current.size) {
                if (eof) return -1
                val next = queue.take()
                if (next === EOF) {
                    eof = true
                    return -1
                }
                current = next
                pos = 0
            }
            val n = minOf(len, current.size - pos)
            System.arraycopy(current, pos, b, off, n)
            pos += n
            return n
        }

        private companion object {
            val EOF = ByteArray(0)
        }
    }

    private class FakeChannel(override val pid: Int = 4242) : PtyChannel {
        private val stream = QueueInputStream()
        private val sent = ByteArrayOutputStream()
        private val exit = CountDownLatch(1)
        @Volatile private var code = 0
        @Volatile var killed = false
        @Volatile var closed = false
        @Volatile var lastResize: List<Int>? = null

        override val input: InputStream get() = stream
        override val output: OutputStream get() = sent
        val sentBytes: ByteArray get() = sent.toByteArray()

        fun emit(data: ByteArray) = stream.feed(data)
        fun emit(text: String) = emit(text.toByteArray(Charsets.UTF_8))
        fun exit(exitCode: Int) {
            code = exitCode
            stream.end()
            exit.countDown()
        }

        override fun resize(rows: Int, columns: Int, cellWidth: Int, cellHeight: Int) {
            lastResize = listOf(rows, columns, cellWidth, cellHeight)
        }

        override fun waitFor(): Int {
            exit.await(5, TimeUnit.SECONDS)
            return code
        }

        override fun kill() {
            killed = true
            exit(-9)
        }

        override fun close() {
            closed = true
        }
    }

    private class FakeClient : TerminalSessionClient {
        val textChanged = AtomicInteger()
        val finished = AtomicInteger()
        @Volatile var shellPid = -1

        override fun onTextChanged(changedSession: TerminalOutput) { textChanged.incrementAndGet() }
        override fun onTitleChanged(changedSession: TerminalOutput) {}
        override fun onSessionFinished(finishedSession: TerminalOutput) { finished.incrementAndGet() }
        override fun onCopyTextToClipboard(session: TerminalOutput, text: String?) {}
        override fun onPasteTextFromClipboard(session: TerminalOutput?) {}
        override fun onBell(session: TerminalOutput) {}
        override fun onColorsChanged(session: TerminalOutput) {}
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun setTerminalShellPid(session: TerminalOutput, pid: Int) { shellPid = pid }
        override fun getTerminalCursorStyle(): Int? = null
        override fun logError(tag: String?, message: String?) {}
        override fun logWarn(tag: String?, message: String?) {}
        override fun logInfo(tag: String?, message: String?) {}
        override fun logDebug(tag: String?, message: String?) {}
        override fun logVerbose(tag: String?, message: String?) {}
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
        override fun logStackTrace(tag: String?, e: Exception?) {}
    }

    /** Pengganti main thread: tugas dikumpulkan dan dijalankan oleh thread tes. */
    private class ManualExecutor : Executor {
        private val queue = LinkedBlockingQueue<Runnable>()
        override fun execute(command: Runnable) = queue.put(command)

        fun runUntil(timeoutMs: Long = 5000, condition: () -> Boolean) {
            val end = System.nanoTime() + timeoutMs * 1_000_000
            while (!condition()) {
                queue.poll(10, TimeUnit.MILLISECONDS)?.run()
                if (System.nanoTime() > end) fail("kondisi tidak tercapai dalam ${timeoutMs}ms")
            }
        }

        fun drainFor(ms: Long) {
            val end = System.nanoTime() + ms * 1_000_000
            while (System.nanoTime() < end) queue.poll(5, TimeUnit.MILLISECONDS)?.run()
        }
    }

    private class Fixture(launcher: ((SessionConfig) -> PtyChannel)? = null) {
        val channel = FakeChannel()
        val client = FakeClient()
        val executor = ManualExecutor()
        val launches = AtomicInteger()
        val session = PtyTerminalSession(
            config = SessionConfig(listOf("/bin/sh")),
            client = client,
            mainExecutor = executor,
            launcher = { config, _, _, _, _ ->
                launches.incrementAndGet()
                launcher?.invoke(config) ?: channel
            },
        )

        fun start() = session.updateSize(80, 24, 8, 16)
        fun transcript(): String = session.emulator!!.screen.transcriptText
    }

    @Test
    fun processStartsOnFirstUpdateSizeOnlyAndLaterSizesResize() {
        val f = Fixture()
        assertNull(f.session.emulator)
        f.start()
        assertNotNull(f.session.emulator)
        assertEquals(1, f.launches.get())
        assertEquals(4242, f.client.shellPid)

        f.session.updateSize(100, 30, 9, 18)
        assertEquals(1, f.launches.get())
        assertEquals(listOf(30, 100, 9, 18), f.channel.lastResize)
        f.session.close()
    }

    @Test
    fun outputReachesEmulatorAndNotifiesClient() {
        val f = Fixture()
        f.start()
        f.channel.emit("halo molina\r\n")
        f.executor.runUntil { f.client.textChanged.get() > 0 }
        assertTrue(f.transcript().contains("halo molina"))
        f.session.close()
    }

    @Test
    fun multibyteCharacterSplitAcrossReadsIsDecoded() {
        val f = Fixture()
        f.start()
        val bytes = "é".toByteArray(Charsets.UTF_8)
        f.channel.emit(byteArrayOf(bytes[0]))
        f.channel.emit(byteArrayOf(bytes[1]))
        f.executor.runUntil { f.client.textChanged.get() >= 2 }
        assertTrue(f.transcript().contains("é"))
        f.session.close()
    }

    @Test
    fun inputIsWrittenToProcessOffTheCallingThread() {
        val f = Fixture()
        f.start()
        f.session.write("ls\n")
        f.executor.runUntil { String(f.channel.sentBytes) == "ls\n" }
        f.session.close()
    }

    @Test
    fun writeCodePointPrependsEscapeWhenAsked() {
        val f = Fixture()
        f.start()
        f.session.writeCodePoint(true, 'a'.code)
        f.executor.runUntil { f.channel.sentBytes.size == 2 }
        assertEquals(listOf<Byte>(27, 'a'.code.toByte()), f.channel.sentBytes.toList())
        f.session.close()
    }

    @Test
    fun invalidCodePointIsRejected() {
        val f = Fixture()
        f.start()
        try {
            f.session.writeCodePoint(false, 0xD800)
            fail("seharusnya melempar IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        f.session.close()
    }

    @Test
    fun exitReportsStatusAfterAllOutputAndOnlyOnce() {
        val f = Fixture()
        f.start()
        f.channel.emit("selesai\r\n")
        f.channel.exit(7)
        f.executor.runUntil { f.client.finished.get() == 1 }
        f.executor.drainFor(100)
        assertEquals(1, f.client.finished.get())
        assertEquals(7, f.session.exitStatus)
        assertFalse(f.session.isRunning)
        val text = f.transcript()
        assertTrue(text.contains("selesai"))
        assertTrue(text.contains("kode 7"))
        assertTrue(f.channel.closed)
    }

    @Test
    fun spawnFailureShowsMessageAndFinishes() {
        val f = Fixture(launcher = { throw IOException("tidak ada /bin/sh") })
        f.start()
        assertEquals(1, f.client.finished.get())
        assertNull(f.session.exitStatus)
        assertTrue(f.transcript().contains("Gagal memulai proses"))
    }

    @Test
    fun closeKillsProcessAndSilencesClient() {
        val f = Fixture()
        f.start()
        f.session.close()
        f.session.close()
        assertTrue(f.channel.killed)
        assertTrue(f.channel.closed)
        f.channel.emit("terlambat")
        f.executor.drainFor(200)
        assertEquals(0, f.client.finished.get())
        assertEquals(0, f.client.textChanged.get())
    }

    @Test
    fun nonPositiveSizeIsIgnored() {
        val f = Fixture()
        f.session.updateSize(0, 24, 8, 16)
        assertEquals(0, f.launches.get())
        assertNull(f.session.emulator)
    }
}
