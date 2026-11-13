package com.molina.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogReportTest {
    private val sample = listOf(
        "potongan awal tanpa header",
        "2026-11-12 14:03:22.123 I/Molina/core: Logger aktif",
        "2026-11-12 14:03:23.000 E/Molina/mpv/p1: gagal memuat berkas",
        "java.lang.IllegalStateException: mpv belum siap",
        "\tat com.molina.cinema.Mpv.load(Mpv.kt:42)",
        "Caused by: java.io.IOException: ENOENT",
        "2026-11-12 14:03:24.500 E/Molina/term/s1: pty putus",
        "2026-11-12 14:03:25.000 W/Molina/proot: lambat",
        "2026-11-12 14:03:26.000 E/Molina/mpv/p1: error lagi",
        "kotlin.NotImplementedError: belum",
    ).joinToString("\n")

    @Test
    fun parseDropsLeadingFragmentAndKeepsStackTrace() {
        val entries = LogReport.parse(sample)
        assertEquals(5, entries.size)
        assertEquals(3, entries[1].extra.size)
        assertEquals("Molina/mpv/p1", entries[1].tag)
    }

    @Test
    fun summaryGroupsErrorsByAreaAndShowsLatestException() {
        val report = LogReport.build("B", LogReport.parse(sample))
        assertTrue("- mpv: 2 error; terakhir: error lagi | kotlin.NotImplementedError: belum" in report)
        assertTrue("- term: 1 error; terakhir: pty putus" in report)
        val summary = report.substringAfter("Error terbaru per area:").substringBefore("Log terakhir")
        assertFalse("proot" in summary)
    }

    @Test
    fun keepsOnlyLastFiftyEntries() {
        val many = (0 until 300).map { LogReport.Entry('I', "Molina/x", "2026-11-12 14:03:22.123 I/Molina/x: m$it", "m$it", emptyList()) }
        val report = LogReport.build("B", many)
        assertTrue("Log terakhir (50 entri)" in report)
        assertTrue("m299" in report)
        assertFalse("m249" in report)
    }

    @Test
    fun respectsCharacterBudgetAndTruncatesLongStacks() {
        val big = (0 until 60).map { LogReport.Entry('I', "Molina/x", "2026-11-12 14:03:22.123 I/Molina/x: " + "z".repeat(2000) + it, "m", emptyList()) }
        assertTrue(LogReport.build("B", big, maxChars = 10_000).length <= 10_000)
        val stack = LogReport.Entry('E', "Molina/files", "2026-11-12 14:03:22.123 E/Molina/files: x", "x", (0 until 20).map { "\tat f$it" })
        assertTrue("(8 baris lagi)" in LogReport.build("B", listOf(stack)))
    }

    @Test
    fun emptyLogIsSafe() {
        assertTrue("Log terakhir (0 entri)" in LogReport.build("B", emptyList()))
    }
}
