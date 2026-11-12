package com.molina.log

import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/** Tulis log ke file secara asinkron (tidak memblokir main thread), dengan rotasi. */
class FileLogSink(
    private val dir: File,
    private val baseName: String = "molina",
    private val maxBytes: Long = 1_000_000,
    private val maxFiles: Int = 3,
) : LogSink {
    init { require(maxFiles >= 2) { "maxFiles minimal 2" } }

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "molina-log").apply { isDaemon = true }
    }

    override fun write(entry: LogEntry) {
        val line = LogFormat.line(entry)
        try {
            executor.execute { append(line) }
        } catch (_: RejectedExecutionException) {
        }
    }

    private fun append(line: String) {
        try {
            dir.mkdirs()
            val current = File(dir, "$baseName.log")
            if (current.length() >= maxBytes) rotate(current)
            current.appendText(line + "\n")
        } catch (_: IOException) {
        }
    }

    private fun rotate(current: File) {
        File(dir, "$baseName.${maxFiles - 1}.log").delete()
        for (i in maxFiles - 2 downTo 1) {
            val f = File(dir, "$baseName.$i.log")
            if (f.exists()) f.renameTo(File(dir, "$baseName.${i + 1}.log"))
        }
        current.renameTo(File(dir, "$baseName.1.log"))
    }

    /** Tunggu antrean tulis selesai (untuk test dan shutdown). */
    fun flush() {
        try { executor.submit {}.get(5, TimeUnit.SECONDS) } catch (_: Exception) {}
    }

    override fun close() {
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }
}
