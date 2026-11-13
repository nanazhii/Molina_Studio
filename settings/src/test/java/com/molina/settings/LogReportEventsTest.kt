// molina-patch12
package com.molina.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogReportEventsTest {
    private val withCrash = listOf(
        "2026-11-13 09:00:00.100 I/Molina/core: Logger aktif",
        "2026-11-13 09:00:01.000 I/Molina/term: [BEHAV:term.session.start] | kind=debian",
        "2026-11-13 09:00:02.000 E/Molina/proot: [BUG:proot.install-failed] unduhan terputus | attempt=1",
        "java.io.IOException: reset",
        "\tat com.molina.proot.Installer.run(Installer.kt:10)",
        "2026-11-13 09:00:03.000 E/Molina/proot: [BUG:proot.install-failed] unduhan terputus | attempt=2",
        "2026-11-13 09:00:04.000 I/Molina/term: [BEHAV:term.session.resize] ukuran berubah | cols=80 rows=24",
        "2026-11-13 09:00:05.500 E/Molina/term: [CRASH:uncaught] exception tidak tertangani | thread=main root=java.lang.IllegalStateException",
        "java.lang.IllegalStateException: pty putus",
        "\tat com.molina.terminal.core.PtyTerminalSession.read(PtyTerminalSession.kt:88)",
        "2026-11-13 09:00:06.000 I/Molina/core: setelah crash",
    ).joinToString("\n")

    private fun report(text: String) = LogReport.build("B", LogReport.parse(text))

    @Test
    fun crashSectionShowsNewestCrashWithStack() {
        val r = report(withCrash)
        assertTrue("Crash terbaru (1 dari 1):" in r)
        val section = r.substringAfter("Crash terbaru").substringBefore("Bug (")
        assertTrue("PtyTerminalSession.read" in section)
        assertTrue("thread=main" in section)
    }

    @Test
    fun bugSectionGroupsByCodeAndKeepsLatestFields() {
        val r = report(withCrash)
        assertTrue("Bug (2 kejadian, 1 jenis):" in r)
        assertTrue("- proot.install-failed (2x); terakhir 09:00:03.000: unduhan terputus | attempt=2" in r)
    }

    @Test
    fun bugSectionAddsExceptionFromLatestOccurrence() {
        val text = withCrash.replace(
            "2026-11-13 09:00:03.000 E/Molina/proot: [BUG:proot.install-failed] unduhan terputus | attempt=2",
            "2026-11-13 09:00:03.000 E/Molina/proot: [BUG:proot.install-failed] unduhan terputus | attempt=2\njava.net.SocketException: putus",
        )
        assertTrue("attempt=2 | java.net.SocketException: putus" in report(text))
    }

    @Test
    fun behaviorSequenceIsChronologicalEndsAtCrashAndSkipsLaterEntries() {
        val r = report(withCrash)
        val section = r.substringAfter("Urutan perilaku sebelum crash").substringBefore("Error terbaru per area")
        val start = section.indexOf("term.session.start")
        val resize = section.indexOf("term.session.resize")
        assertTrue(start in 0 until resize)
        assertTrue("BUG proot.install-failed" in section)
        assertTrue("cols=80 rows=24" in section)
        assertTrue("09:00:05.500 CRASH uncaught  <-- proses berhenti" in section)
        assertFalse("setelah crash" in section)
        assertFalse("Logger aktif" in section)
    }

    @Test
    fun behaviorSequenceKeepsOnlyLatestN() {
        val many = (0 until 30).joinToString("\n") {
            "2026-11-13 09:00:00.000 I/Molina/term: [BEHAV:term.step$it] | n=$it"
        } + "\n2026-11-13 09:01:00.000 E/Molina/term: [CRASH:uncaught] mati | thread=main"
        val section = report(many).substringAfter("Urutan perilaku sebelum crash").substringBefore("Error terbaru")
        assertTrue("(15 peristiwa terakhir)" in section)
        assertTrue("term.step29" in section)
        assertFalse("term.step14 " in section)
    }

    @Test
    fun crashWithoutEarlierEventsSaysSo() {
        val r = report("2026-11-13 09:01:00.000 E/Molina/term: [CRASH:uncaught] mati | thread=main")
        assertTrue("(tidak ada peristiwa BEHAV/BUG tercatat sebelum crash)" in r)
    }

    @Test
    fun withoutCrashShowsLatestBehaviorsOnly() {
        val r = report(withCrash.substringBefore("2026-11-13 09:00:05.500"))
        assertFalse("Crash terbaru" in r)
        assertTrue("Perilaku terakhir (tanpa crash, 4 peristiwa):" in r)
        assertTrue("Bug (2 kejadian, 1 jenis):" in r)
    }

    @Test
    fun plainLogHasNoStructuredSections() {
        val r = report(
            "2026-11-13 09:00:00.100 I/Molina/core: Logger aktif\n" +
                "2026-11-13 09:00:01.000 E/Molina/mpv/p1: gagal memuat\n",
        )
        assertFalse("Crash terbaru" in r)
        assertFalse("Bug (" in r)
        assertFalse("Perilaku" in r)
        assertTrue("- mpv: 1 error; terakhir: gagal memuat" in r)
    }

    @Test
    fun malformedMarkerIsTreatedAsPlainLine() {
        val r = report("2026-11-13 09:00:00.100 E/Molina/term: [BUG:Kode Salah] x")
        assertFalse("Bug (" in r)
    }
}
