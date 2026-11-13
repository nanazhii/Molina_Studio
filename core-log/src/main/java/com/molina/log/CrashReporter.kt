// molina-patch11
package com.molina.log

import java.util.concurrent.atomic.AtomicBoolean

internal fun causeChain(t: Throwable): List<Throwable> {
    val out = ArrayList<Throwable>()
    var c: Throwable? = t
    while (out.size < 16) {
        val cur = c ?: break
        if (out.any { it === cur }) break
        out.add(cur)
        c = cur.cause
    }
    return out
}

internal fun rootCauseOf(t: Throwable): Throwable = causeChain(t).last()

/** Menentukan area (segmen kedua tag) dari stack trace, agar crash masuk ke area yang benar. */
object CrashAttribution {
    private val MPV_CLASS = Regex("(?i)(mpv|player)")

    private val PREFIXES = listOf(
        "com.molina.terminal.proot." to "proot",
        "com.molina.terminal." to "term",
        "com.molina.files." to "files",
        "com.molina.settings." to "settings",
        "com.molina.log." to "core",
        "com.molina.core." to "core",
        "com.molina.studio." to "app",
    )

    /** Penyebab terdalam dulu; frame Molina pertama menentukan area. Tanpa frame Molina: core. */
    fun areaOf(t: Throwable): String {
        for (c in causeChain(t).asReversed()) {
            for (f in c.stackTrace) {
                areaOfClass(f.className)?.let { return it }
            }
        }
        return "core"
    }

    fun areaOfClass(className: String): String? {
        if (className.startsWith("com.molina.cinema.")) {
            val simple = className.substringAfterLast('.')
            return if (MPV_CLASS.containsMatchIn(simple)) "mpv" else "cinema"
        }
        for ((prefix, area) in PREFIXES) {
            if (className.startsWith(prefix)) return area
        }
        return null
    }
}

/**
 * Handler exception tak tertangani: catat crash (stack trace lengkap) ke logger, tunggu antrean
 * file log tuntas, lalu teruskan ke handler sebelumnya agar proses tetap mati seperti biasa.
 * Crash native (SIGSEGV di libmpv) tidak lewat sini; lihat docs/log-contract.md.
 */
class CrashReporter(
    private val logger: () -> MolinaLogger = { MolinaLog.logger },
    private val previous: Thread.UncaughtExceptionHandler? = null,
) : Thread.UncaughtExceptionHandler {

    private val handling = AtomicBoolean(false)

    override fun uncaughtException(thread: Thread, t: Throwable) {
        if (handling.compareAndSet(false, true)) {
            try {
                val l = logger()
                MolinaEvents.crash(CrashAttribution.areaOf(t), t, thread.name, logger = l)
                l.flush()
            } catch (_: Throwable) {
                // logging tidak boleh menutupi crash aslinya
            }
            try {
                previous?.uncaughtException(thread, t)
            } finally {
                handling.set(false)
            }
        } else {
            previous?.uncaughtException(thread, t)
        }
    }

    companion object {
        /** Idempotent: memanggil dua kali tidak menumpuk handler. */
        fun install(logger: () -> MolinaLogger = { MolinaLog.logger }): CrashReporter {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            if (current is CrashReporter) return current
            val reporter = CrashReporter(logger, current)
            Thread.setDefaultUncaughtExceptionHandler(reporter)
            return reporter
        }
    }
}
