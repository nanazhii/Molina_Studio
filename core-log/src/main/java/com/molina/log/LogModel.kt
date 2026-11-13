package com.molina.log

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel(val letter: Char, val priority: Int) {
    DEBUG('D', 3), INFO('I', 4), WARN('W', 5), ERROR('E', 6)
}

data class LogEntry(
    val timeMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null,
)

interface LogSink {
    fun write(entry: LogEntry)
    fun close() {}

    /** Pastikan entri yang sudah ditulis benar-benar sampai ke tujuan (mis. sebelum proses mati). */
    fun flush() {}
}

object LogFormat {
    /** Format: 2026-11-12 14:03:22.123 I/Molina/term/s1: pesan */
    fun line(e: LogEntry): String {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(e.timeMillis))
        val sb = StringBuilder()
            .append(ts).append(' ').append(e.level.letter).append('/')
            .append(e.tag).append(": ").append(e.message)
        e.throwable?.let { sb.append('\n').append(it.stackTraceToString()) }
        return sb.toString()
    }
}
