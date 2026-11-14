// molina-patch26e1
package com.molina.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerRecentTest {
    private val kv = MemoryKeyValueStore()
    private val recent = ExplorerRecent(kv)
    private val all: (String) -> Boolean = { true }

    @Test fun kosongAwalnya() {
        assertNull(recent.last(all))
        assertEquals(emptyList<String>(), recent.load(all))
    }

    @Test fun terbaruDiDepan() {
        recent.push("a"); recent.push("b"); recent.push("c")
        assertEquals(listOf("c", "b", "a"), recent.load(all))
        assertEquals("c", recent.last(all))
    }

    @Test fun pushUlangMemindahkanKeDepanTanpaDuplikat() {
        recent.push("a"); recent.push("b"); recent.push("a")
        assertEquals(listOf("a", "b"), recent.paths())
    }

    @Test fun maksimalLimaDanYangTertuaTerbuang() {
        for (n in 1..7) recent.push("f$n")
        assertEquals(listOf("f7", "f6", "f5", "f4", "f3"), recent.paths())
    }

    @Test fun batasKustom() {
        val r = ExplorerRecent(MemoryKeyValueStore(), max = 2)
        r.push("a"); r.push("b"); r.push("c")
        assertEquals(listOf("c", "b"), r.paths())
    }

    @Test fun rootBisaDicatatDanDibedakanDariKosong() {
        assertTrue(recent.push(""))
        assertEquals("", recent.last(all))
        assertEquals(listOf(""), recent.paths())
    }

    @Test fun pathTidakValidDitolak() {
        assertFalse(recent.push("a/../b"))
        assertFalse(recent.push("x\ny"))
        assertEquals(emptyList<String>(), recent.paths())
    }

    @Test fun loadMenyaringFolderYangHilang() {
        recent.push("ada"); recent.push("hilang")
        assertEquals("ada", recent.last { it == "ada" })
        assertEquals(listOf("ada"), recent.load { it == "ada" })
    }

    @Test fun tersimpanLintasInstance() {
        recent.push("p/q")
        assertEquals(listOf("p/q"), ExplorerRecent(kv).paths())
    }
}
