package com.molina.log

import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReporterTest {
    private class CaptureSink : LogSink {
        val entries = CopyOnWriteArrayList<LogEntry>()
        @Volatile var flushes = 0
        @Volatile var failWrites = false
        override fun write(entry: LogEntry) {
            if (failWrites) throw IllegalStateException("sink rusak")
            entries += entry
        }
        override fun flush() { flushes++ }
    }

    private fun trace(vararg classes: String): Throwable = RuntimeException("x").apply {
        stackTrace = classes.map { StackTraceElement(it, "m", "F.kt", 1) }.toTypedArray()
    }

    @Test
    fun attributionMapsPackagesToAreas() {
        fun area(vararg c: String) = CrashAttribution.areaOf(trace(*c))
        assertEquals("proot", area("android.view.View", "com.molina.terminal.proot.RootfsInstaller"))
        assertEquals("term", area("com.molina.terminal.core.PtyTerminalSession"))
        assertEquals("term", area("com.molina.terminal.TerminalFragment"))
        assertEquals("mpv", area("com.molina.cinema.MPVLib"))
        assertEquals("mpv", area("com.molina.cinema.LoggingMolinaPlayerSession"))
        assertEquals("cinema", area("com.molina.cinema.CinemaFragment"))
        assertEquals("files", area("com.molina.files.FilesFragment"))
        assertEquals("settings", area("com.molina.settings.LogReport"))
        assertEquals("app", area("com.molina.studio.MainActivity"))
        assertEquals("core", area("com.molina.core.storage.X"))
        assertEquals("core", area("android.os.Handler", "java.lang.Thread"))
    }

    @Test
    fun attributionPrefersRootCause() {
        val inner = trace("com.molina.terminal.proot.RootfsInstaller")
        val outer = RuntimeException("luar", inner).apply { stackTrace = arrayOf(StackTraceElement("com.molina.files.Browser", "m", "F.kt", 1)) }
        assertEquals("proot", CrashAttribution.areaOf(outer))
    }

    @Test
    fun logsCrashFlushesAndDelegates() {
        val sink = CaptureSink()
        val log = MolinaLogger(listOf(sink), clock = { 0L })
        var delegated: Throwable? = null
        val reporter = CrashReporter({ log }, Thread.UncaughtExceptionHandler { _, e -> delegated = e })
        val boom = trace("com.molina.cinema.MpvNative")
        reporter.uncaughtException(Thread.currentThread(), boom)

        val e = sink.entries.single()
        assertEquals("Molina/mpv", e.tag)
        assertEquals(LogLevel.ERROR, e.level)
        assertSame(boom, e.throwable)
        assertTrue(e.message.startsWith("[CRASH:uncaught]"))
        assertTrue(sink.flushes >= 1)
        assertSame(boom, delegated)
    }

    @Test
    fun brokenSinkNeverHidesTheCrash() {
        val sink = CaptureSink().also { it.failWrites = true }
        val log = MolinaLogger(listOf(sink))
        var delegated = 0
        val reporter = CrashReporter({ log }, Thread.UncaughtExceptionHandler { _, _ -> delegated++ })
        reporter.uncaughtException(Thread.currentThread(), trace("x.Y"))
        assertEquals(1, delegated)
    }

    @Test
    fun loggerFactoryFailureStillDelegates() {
        var delegated = 0
        val reporter = CrashReporter({ error("logger belum siap") }, Thread.UncaughtExceptionHandler { _, _ -> delegated++ })
        reporter.uncaughtException(Thread.currentThread(), trace("x.Y"))
        assertEquals(1, delegated)
    }

    @Test
    fun installIsIdempotent() {
        val saved = Thread.getDefaultUncaughtExceptionHandler()
        try {
            val first = CrashReporter.install()
            val second = CrashReporter.install()
            assertSame(first, second)
            assertSame(first, Thread.getDefaultUncaughtExceptionHandler())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(saved)
        }
    }
}
