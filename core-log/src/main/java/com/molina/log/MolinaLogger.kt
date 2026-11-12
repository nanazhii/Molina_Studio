package com.molina.log

class MolinaLogger(
    private val sinks: List<LogSink>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun log(level: LogLevel, tag: String, message: String, t: Throwable? = null) {
        val entry = LogEntry(clock(), level, tag, message, t)
        for (s in sinks) runCatching { s.write(entry) }
    }
    fun d(tag: String, msg: String) = log(LogLevel.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = log(LogLevel.INFO, tag, msg)
    fun w(tag: String, msg: String, t: Throwable? = null) = log(LogLevel.WARN, tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = log(LogLevel.ERROR, tag, msg, t)
    fun close() = sinks.forEach { runCatching { it.close() } }

    companion object {
        /** Konvensi tag: Molina/<area>[/<id>], mis. Molina/term/s1, Molina/mpv/p1. */
        fun tag(area: String, id: String? = null): String =
            if (id == null) "Molina/$area" else "Molina/$area/$id"
    }
}

/** Akses global; sebelum init() semua log dibuang (aman untuk unit test). */
object MolinaLog {
    @Volatile
    var logger: MolinaLogger = MolinaLogger(emptyList())
        private set

    fun install(l: MolinaLogger) { logger = l }
}
