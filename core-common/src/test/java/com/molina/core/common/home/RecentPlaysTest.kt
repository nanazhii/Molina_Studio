package com.molina.core.common.home

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentPlaysTest {
    private class MemStore(var text: String = "") : RecentStore {
        override fun read() = text
        override fun write(text: String) { this.text = text }
    }

    @Test fun newestFirstAndDuplicatesMoveToTop() {
        val r = RecentPlays(MemStore())
        r.record("https://a/1", "A", 1)
        r.record("https://b/2", "B", 2)
        val list = r.record("https://a/1", "A2", 3)
        assertEquals(listOf("https://a/1", "https://b/2"), list.map { it.target })
        assertEquals("A2", list[0].title)
    }

    @Test fun capIsEnforced() {
        val r = RecentPlays(MemStore(), max = 3)
        for (i in 1..5) r.record("https://h/$i", "t$i", i.toLong())
        assertEquals(listOf("https://h/5", "https://h/4", "https://h/3"), r.list().map { it.target })
    }

    @Test fun roundTripKeepsTabsNewlinesAndBackslashes() {
        val store = MemStore()
        RecentPlays(store).record("/sdcard/a\\b\tc.mp4", "judul\ndua", 7)
        val e = RecentPlays(store).list().single()
        assertEquals("/sdcard/a\\b\tc.mp4", e.target)
        assertEquals("judul\ndua", e.title)
        assertEquals(7L, e.atMs)
    }

    @Test fun removeClearAndBlankTarget() {
        val r = RecentPlays(MemStore())
        r.record("https://a/1", "", 1)
        r.record("   ", "x", 2)
        assertEquals(1, r.list().size)
        assertEquals("https://a/1", r.list()[0].title)
        assertEquals(0, r.remove("https://a/1").size)
        r.record("https://a/1", "A", 3); r.clear()
        assertEquals(0, r.list().size)
    }

    @Test fun corruptLinesAreSkipped() {
        assertEquals(0, RecentPlays.decode("bukan angka\tx\ty\n\nrusak").size)
    }

    @Test fun titleHidesQueryAndCredentials() {
        assertEquals("host.tv · live", RecentTitle.of("https://user:pw@host.tv/a/live?token=rahasia#x"))
        assertEquals("host.tv", RecentTitle.of("https://host.tv/"))
        assertEquals("film.mkv", RecentTitle.of("/sdcard/Movies/film.mkv"))
    }
}
