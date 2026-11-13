// molina-patch13c
package com.molina.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFilterSessionTest {
    private val raw = listOf(
        "11-13 09:00:01.000  4242  4242 I Molina/term/t1: [BEHAV:term.session.start] | kind=debian",
        "11-13 09:00:01.100  4242  4242 I Molina/proot/t1: [BEHAV:proot.rootfs-checked] | readable=true bash=true env=true",
        "11-13 09:00:02.000  4242  4242 E Molina/term/t1: [BUG:term.exit-early] proses keluar sebelum 5 detik | code=127 uptime_ms=800",
        "11-13 09:00:03.000  4242  4242 I Molina/term/t2: [BEHAV:term.session.start] | kind=android-shell",
        "11-13 09:00:04.000  4242  4242 I Molina/files: [BEHAV:files.list] | depth=0 count=3",
        "11-13 09:00:05.000  1111  1111 I OtherApp/x/t1: bukan molina",
    )
    private val all = LogFilter.classify(raw)

    @Test
    fun sessionOfReadsThirdSegmentOfMolinaTagsOnly() {
        assertEquals("t1", LogFilter.sessionOf("Molina/term/t1"))
        assertNull(LogFilter.sessionOf("Molina/files"))
        assertNull(LogFilter.sessionOf("OtherApp/x/t1"))
        assertNull(LogFilter.sessionOf(""))
    }

    @Test
    fun sessionsListIdsInOrderOfFirstAppearance() {
        assertEquals(listOf("t1", "t2"), LogFilter.sessions(all))
        assertEquals(listOf("t2"), LogFilter.sessions(all, max = 1))
    }

    @Test
    fun selectBySessionSpansAreasAndKeepsOrder() {
        val s = LogFilter.select(all, EventFilter.ALL, false, "t1")
        assertEquals(3, s.size)
        assertTrue("term.session.start" in s[0].text)
        assertTrue("proot.rootfs-checked" in s[1].text)
        assertTrue("term.exit-early" in s[2].text)
    }

    @Test
    fun sessionCombinesWithKindFilterAndDefaultKeepsEverything() {
        val bug = LogFilter.select(all, EventFilter.BUG, false, "t1")
        assertEquals(1, bug.size)
        assertEquals(raw.size, LogFilter.select(all, EventFilter.ALL, false).size)
    }

    @Test
    fun copyTextHasBuildLineHeaderAndTruncationNote() {
        val shown = LogFilter.select(all, EventFilter.ALL, false, "t1")
        val full = LogFilter.copyText("Molina 0.1.0 (1)", shown, EventFilter.ALL, false, "t1")
        assertTrue(full.startsWith("Molina 0.1.0 (1)\nTampilan: Semua | sesi=t1 | hanya-Molina=tidak | 3 baris\n"))
        assertTrue(raw[2] in full)
        val cut = LogFilter.copyText("b", shown, EventFilter.ALL, false, null, max = 2)
        assertTrue("sesi=semua" in cut)
        assertTrue("(1 baris lebih lama dipangkas)" in cut)
        assertTrue(raw[0] !in cut)
    }
}
