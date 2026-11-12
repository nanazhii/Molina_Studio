package com.molina.terminal.core

import com.molina.terminal.pty.PtyProcess
import java.io.InputStream
import java.io.OutputStream

/**
 * Peluncur bawaan berbasis `terminal-pty` (JNI).
 *
 * Lingkungan proses induk TIDAK diwariskan. Hanya `TERM=xterm-256color` yang diisi bila
 * [SessionConfig.env] tidak menyebutnya; sisanya harus disediakan pemanggil.
 */
object DefaultPtyLauncher : PtyLauncher {

    private const val DEFAULT_CWD = "/"

    override fun launch(config: SessionConfig, rows: Int, columns: Int, cellWidth: Int, cellHeight: Int): PtyChannel {
        require(config.command.isNotEmpty()) { "command kosong" }
        val env = LinkedHashMap<String, String>().apply {
            put("TERM", "xterm-256color")
            putAll(config.env)
        }.map { (k, v) -> "$k=$v" }.toTypedArray()

        val process = PtyProcess.spawn(
            command = config.command.first(),
            cwd = config.workingDir ?: DEFAULT_CWD,
            args = config.command.toTypedArray(),
            env = env,
            rows = rows,
            columns = columns,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
        )
        return object : PtyChannel {
            override val pid: Int get() = process.pid
            override val input: InputStream get() = process.input
            override val output: OutputStream get() = process.output
            override fun resize(rows: Int, columns: Int, cellWidth: Int, cellHeight: Int) =
                process.resize(rows, columns, cellWidth, cellHeight)
            override fun waitFor(): Int = process.waitFor()
            override fun kill() { process.kill() }
            override fun close() = process.close()
        }
    }
}
