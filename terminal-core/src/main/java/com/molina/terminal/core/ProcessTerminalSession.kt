package com.molina.terminal.core

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Implementasi awal berbasis ProcessBuilder (tanpa PTY).
 * Diganti/ditambah implementasi PTY (JNI) tanpa mengubah antarmuka TerminalSession.
 */
class ProcessTerminalSession(private val config: SessionConfig) : TerminalSession {

    @Volatile
    private var process: Process? = null

    override val events: Flow<SessionEvent> = callbackFlow {
        val proc = try {
            ProcessBuilder(config.command).apply {
                config.workingDir?.let { directory(File(it)) }
                environment().putAll(config.env)
                redirectErrorStream(true)
            }.start()
        } catch (e: IOException) {
            send(SessionEvent.Failed(e))
            close()
            return@callbackFlow
        }
        process = proc

        val reader = launch(Dispatchers.IO) {
            try {
                val buf = CharArray(4096)
                proc.inputStream.reader().use { r ->
                    while (true) {
                        val n = r.read(buf)
                        if (n < 0) break
                        send(SessionEvent.Output(String(buf, 0, n)))
                    }
                }
            } catch (_: IOException) {
                // stream ditutup saat close()
            }
            send(SessionEvent.Exited(proc.waitFor()))
            close()
        }
        awaitClose {
            reader.cancel()
            proc.destroy()
        }
    }

    override fun write(input: String) {
        try {
            process?.outputStream?.apply {
                write(input.toByteArray())
                flush()
            }
        } catch (_: IOException) {
            // proses sudah selesai
        }
    }

    override fun close() {
        process?.destroy()
    }
}
