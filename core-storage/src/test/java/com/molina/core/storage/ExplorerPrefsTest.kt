// molina-patch26e1
package com.molina.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerPrefsTest {
    private val kv = MemoryKeyValueStore()
    private val prefs = ExplorerPrefs(kv)

    @Test fun bawaanSamaDenganRepoLama() {
        val d = prefs.load()
        assertEquals(SortKey.NAME, d.sortKey)
        assertTrue(d.ascending)
        assertTrue(d.foldersFirst)
        assertFalse(d.showHidden)
        assertFalse(d.deep)
        assertFalse(d.playedFirst)
    }

    @Test fun simpanLaluMuat() {
        val d = ExplorerPrefs.Data(SortKey.SIZE, false, false, true, true, true)
        prefs.save(d)
        assertEquals(d, ExplorerPrefs(kv).load())
    }

    @Test fun kunciSortirRusakKembaliKeNama() {
        kv.putString("sort_key", "BUKAN_KUNCI")
        assertEquals(SortKey.NAME, prefs.load().sortKey)
    }

    @Test fun applyToMempertahankanKueriDanFilter() {
        val base = ListOptions(query = "mkv", onlyExt = setOf("mkv"))
        val out = ExplorerPrefs.Data(SortKey.DATE, false, false, true, true).applyTo(base)
        assertEquals(SortKey.DATE, out.sortKey)
        assertFalse(out.ascending)
        assertFalse(out.foldersFirst)
        assertTrue(out.showHidden)
        assertTrue(out.deep)
        assertEquals("mkv", out.query)
        assertEquals(setOf("mkv"), out.onlyExt)
    }

    @Test fun fromOptionsBolakBalikDenganApplyTo() {
        val o = ListOptions(sortKey = SortKey.TYPE, ascending = false, showHidden = true)
        assertEquals(o, ExplorerPrefs.Data.from(o).applyTo(ListOptions()))
    }
}
