package com.molina.log

import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LogEventsTest {
    private class CaptureSink : LogSink {
        val entries = CopyOnWriteArrayList<LogEntry>()
        override fun write(entry: LogEntry) { entries += entry }
    }

    @Test
    fun formatAndParseRoundTrip() {
        val msg = LogEvents.format(EventKind.BUG, "proot.exec-failed", "proot gagal jalan", mapOf("exit" to "127", "path" to "a b/c"))
        assertEquals("[BUG:proot.exec-failed] proot gagal jalan | exit=127 path=a_b/c", msg)
        val e = LogEvents.parse(msg)!!
        assertEquals(EventKind.BUG, e.kind)
        assertEquals("proot.exec-failed", e.code)
        assertEquals("proot gagal jalan", e.summary)
        assertEquals("127", e.fields["exit"])
        assertEquals("a_b/c", e.fields["path"])
    }

    @Test
    fun summaryOptionalAndFieldsOptional() {
        assertEquals("[BEHAV:term.session.start]", LogEvents.format(EventKind.BEHAVIOR, "term.session.start"))
        val onlyFields = LogEvents.format(EventKind.BEHAVIOR, "term.session.start", fields = mapOf("kind" to "debian"))
        assertEquals("[BEHAV:term.session.start] | kind=debian", onlyFields)
        assertEquals("debian", LogEvents.parse(onlyFields)!!.fields["kind"])
        assertEquals(emptyMap<String, String>(), LogEvents.parse("[BEHAV:term.session.start]")!!.fields)
    }

    @Test
    fun neverThrowsAndSanitizes() {
        assertEquals("unknown", LogEvents.normalizeCode("   "))
        assertEquals("a-b.c", LogEvents.normalizeCode("A B.c"))
        val msg = LogEvents.format(EventKind.BUG, "X Y", "baris1\nbaris2 | lain", mapOf("K e" to "v\n1|2"))
        assertFalse(msg.contains('\n'))
        val e = LogEvents.parse(msg)!!
        assertEquals("x-y", e.code)
        assertEquals("baris1 baris2 / lain", e.summary)
        assertEquals("v_1/2", e.fields["k_e"])
        assertTrue(LogEvents.format(EventKind.BUG, "x", "z".repeat(1000)).length < 200)
    }

    @Test
    fun parseRejectsPlainMessages() {
        assertNull(LogEvents.parse("Logger aktif"))
        assertNull(LogEvents.parse("[CRASHED:x] salah"))
    }

    @Test
    fun emitHelpersUseConventionTagsAndLevels() {
        val sink = CaptureSink()
        val log = MolinaLogger(listOf(sink), clock = { 0L })
        val boom = IllegalStateException("mpv belum siap")
        MolinaEvents.crash("mpv", boom, "main", logger = log)
        MolinaEvents.bug("proot", "install-failed", "unduhan terputus", boom, id = "s1", logger = log)
        MolinaEvents.bug("proot", "slow", "lambat", level = LogLevel.WARN, logger = log)
        MolinaEvents.behavior("term", "session.start", fields = mapOf("kind" to "debian"), logger = log)

        val (crash, bug, warn, behav) = sink.entries
        assertEquals("Molina/mpv", crash.tag)
        assertEquals(LogLevel.ERROR, crash.level)
        assertSame(boom, crash.throwable)
        val ce = LogEvents.parse(crash.message)!!
        assertEquals(EventKind.CRASH, ce.kind)
        assertEquals("main", ce.fields["thread"])
        assertEquals("java.lang.IllegalStateException", ce.fields["root"])

        assertEquals("Molina/proot/s1", bug.tag)
        assertEquals(LogLevel.ERROR, bug.level)
        assertEquals(LogLevel.WARN, warn.level)
        assertEquals("Molina/term", behav.tag)
        assertEquals(LogLevel.INFO, behav.level)
        assertNotNull(LogEvents.parse(behav.message))
    }

    @Test
    fun eventLineKeepsLogcatHeaderFormat() {
        val e = LogEntry(0L, LogLevel.ERROR, "Molina/term", LogEvents.format(EventKind.CRASH, "uncaught", "x"))
        assertTrue(Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} E/Molina/term: \[CRASH:uncaught] x$""").matches(LogFormat.line(e)))
    }

    @Test
    fun qualifyPrefixesAreaOnce() {
        assertEquals("proot.install-failed", LogEvents.qualify("proot", "install-failed"))
        assertEquals("proot.install-failed", LogEvents.qualify("proot", "proot.install-failed"))
        assertEquals("mpv.error", LogEvents.qualify("mpv", "Error"))
        assertEquals("term.session.start", LogEvents.qualify("term", "session.start"))
    }
}
