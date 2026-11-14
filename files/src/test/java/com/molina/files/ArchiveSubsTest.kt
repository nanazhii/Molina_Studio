// molina-patch26k2
package com.molina.files

import com.molina.core.storage.ArchiveEntryInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveSubsTest {
    private fun f(name: String) = ArchiveEntryInfo(name, 10L, false)

    @Test fun mengenaliEkstensiSubtitle() {
        for (n in listOf("a.srt", "A.SRT", "b.ass", "c.ssa", "d.vtt", "e.sub", "f.idx", "g.sup", "h.smi")) {
            assertTrue(n, ArchiveSubs.isSubtitle(n))
        }
        assertFalse(ArchiveSubs.isSubtitle("film.mkv"))
        assertFalse(ArchiveSubs.isSubtitle("srt"))
        assertFalse(ArchiveSubs.isSubtitle("catatan.txt"))
    }

    @Test fun memilihHanyaBerkasSubtitleBersih() {
        val picked = ArchiveSubs.pick(
            listOf(
                f("Film.srt"),
                f("Subs/Film.id.srt"),
                ArchiveEntryInfo("Subs/", 0L, true),
                f("Film.mkv"),
                f("__MACOSX/Subs/._Film.srt"),
                f("Subs/._Film.ass"),
                f("../keluar.srt"),
                f("\\Subs\\Film.en.ass"),
            ),
        )
        assertEquals(setOf("Film.srt", "Subs/Film.id.srt", "Subs/Film.en.ass"), picked)
    }

    @Test fun jumlahDibatasi() {
        val many = (1..(ArchiveSubs.MAX + 50)).map { f("s$it.srt") }
        assertEquals(ArchiveSubs.MAX, ArchiveSubs.pick(many).size)
    }

    @Test fun kosongBilaTidakAda() {
        assertTrue(ArchiveSubs.pick(listOf(f("a.txt"), f("b.nfo"))).isEmpty())
    }
}
