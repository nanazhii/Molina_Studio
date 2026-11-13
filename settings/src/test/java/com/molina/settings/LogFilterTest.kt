// molina-patch12
package com.molina.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFilterTest {
    private val raw = listOf(
        "--------- beginning of main",
        "11-13 09:00:01.000  4242  4242 I Molina/term: [BEHAV:term.session.start] | kind=debian",
        "11-13 09:00:02.000  4242  4242 E Molina/proot: [BUG:proot.install-failed] gagal | attempt=1",
        "11-13 09:00:02.001  4242  4242 E Molina/proot: java.io.IOException: reset",
        "11-13 09:00:02.002  4242  4242 E Molina/proot: \tat com.molina.proot.Installer.run(Installer.kt:10)",
        "11-13 09:00:03.000  4242  4242 D Molina/term: baris biasa",
        "11-13 09:00:04.000  4242  4242 E Molina/term: [CRASH:uncaught] mati | thread=main root=x",
        "11-13 09:00:04.001  4242  4242 E Molina/term: java.lang.IllegalStateException: pty putus",
        "11-13 09:00:04.002  4242  4243 E Molina/term: \tat beda.thread(X.kt:1)",
        "11-13 09:00:04.003  4242  4242 E AndroidRuntime: FATAL EXCEPTION: main",
        "11-13 09:00:05.000  1111  1111 E OtherApp: [BUG:x] bukan molina",
        "11-13 09:00:06.000  4242  4242 E Molina/x   : [BUG:y] tag diberi spasi",
    )
    private val all = LogFilter.classify(raw)

    private fun kinds() = all.map { it.kind }

    @Test
    fun classifiesMarkersOnlyOnMolinaTags() {
        val k = kinds()
        assertEquals(EventFilter.ALL, k[0])
        assertEquals(EventFilter.BEHAV, k[1])
        assertEquals(EventFilter.BUG, k[2])
        assertEquals(EventFilter.CRASH, k[6])
        assertEquals(EventFilter.ALL, k[10]) // tag OtherApp
        assertEquals(EventFilter.BUG, k[11]) // tag dengan spasi kanan
    }

    @Test
    fun stackLinesFollowTheirParentOnlyWithSameProcessThreadAndTag() {
        val k = kinds()
        assertEquals(EventFilter.BUG, k[3])
        assertEquals(EventFilter.BUG, k[4])
        assertFalse(all[3].head)
        assertEquals(EventFilter.ALL, k[5]) // baris biasa memutus lanjutan
        assertEquals(EventFilter.CRASH, k[7])
        assertEquals(EventFilter.ALL, k[8]) // thread beda
        assertEquals(EventFilter.ALL, k[9]) // tag beda
    }

    @Test
    fun selectCrashKeepsHeadAndItsStack() {
        val s = LogFilter.select(all, EventFilter.CRASH, false)
        assertEquals(2, s.size)
        assertTrue("[CRASH:uncaught]" in s[0].text)
        assertTrue("IllegalStateException" in s[1].text)
    }

    @Test
    fun selectBugIncludesItsStackAndCombinesWithOnlyMolina() {
        assertEquals(4, LogFilter.select(all, EventFilter.BUG, true).size)
        assertTrue(LogFilter.select(all, EventFilter.BUG, false).none { "OtherApp" in it.text })
    }

    @Test
    fun allWithOnlyMolinaMatchesLegacyBehavior() {
        val legacy = raw.filter { it.contains(" Molina/") }
        assertEquals(legacy, LogFilter.select(all, EventFilter.ALL, true).map { it.text })
        assertEquals(raw, LogFilter.select(all, EventFilter.ALL, false).map { it.text })
    }

    @Test
    fun countsOnlyEventHeads() {
        val c = LogFilter.counts(all)
        assertEquals(1, c[EventFilter.CRASH])
        assertEquals(2, c[EventFilter.BUG])
        assertEquals(1, c[EventFilter.BEHAV])
    }

    @Test
    fun nonLogcatLinesAreHiddenByEventFilters() {
        assertTrue(LogFilter.select(all, EventFilter.BEHAV, false).none { "beginning of main" in it.text })
        assertEquals(1, LogFilter.select(all, EventFilter.BEHAV, false).size)
    }

    @Test
    fun emptyInputIsSafe() {
        val e = LogFilter.classify(emptyList())
        assertTrue(e.isEmpty())
        assertEquals(0, LogFilter.counts(e)[EventFilter.CRASH])
    }
}
