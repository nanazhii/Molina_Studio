// molina-patch26c
package com.molina.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilesSelectionTest {
    private val s = FilesSelection()

    @Test fun geserKananMasukModeDanMemilih() {
        assertFalse(s.active)
        assertTrue(s.select("a"))
        assertTrue(s.active)
        assertEquals(1, s.count)
        s.select("b")
        assertEquals(listOf("a", "b"), s.snapshot())
    }

    @Test fun geserKiriMembatalkanDanKeluarOtomatisSaatNol() {
        s.select("a")
        s.select("b")
        assertTrue(s.deselect("a"))
        assertTrue(s.active)
        assertTrue(s.deselect("b"))
        assertFalse(s.active)
        assertEquals(0, s.count)
    }

    @Test fun geserKiriPadaYangTidakTerpilihTidakMengubahApaApa() {
        s.select("a")
        assertFalse(s.deselect("x"))
        assertTrue(s.active)
        assertEquals(1, s.count)
    }

    @Test fun ketukMembalikStatusDanKeluarSaatNol() {
        assertTrue(s.toggle("a"))
        assertTrue(s.toggle("b"))
        assertFalse(s.toggle("a"))
        assertTrue(s.active)
        assertFalse(s.toggle("b"))
        assertFalse(s.active)
    }

    @Test fun modeDariMenuTetapAktifDenganNolTerpilih() {
        s.enter()
        assertTrue(s.active)
        assertEquals(0, s.count)
    }

    @Test fun pilihSemua() {
        s.selectAll(listOf("a", "b", "c"))
        assertTrue(s.active)
        assertEquals(3, s.count)
        assertTrue(s.contains("b"))
    }

    @Test fun keluarMengosongkan() {
        s.select("a")
        s.exit()
        assertFalse(s.active)
        assertEquals(0, s.count)
    }

    @Test fun retainOnlyMembuangYangHilang() {
        s.selectAll(listOf("a", "b"))
        assertTrue(s.retainOnly(setOf("a", "z")))
        assertEquals(listOf("a"), s.snapshot())
        assertTrue(s.active)
    }

    @Test fun retainOnlyKeluarBilaSemuaHilang() {
        s.select("a")
        s.retainOnly(setOf("z"))
        assertFalse(s.active)
    }

    @Test fun retainOnlyTidakMengeluarkanModeKosongDariMenu() {
        s.enter()
        assertFalse(s.retainOnly(setOf("z")))
        assertTrue(s.active)
    }

    @Test fun restoreMengembalikanKeadaan() {
        s.restore(true, listOf("a", "b"))
        assertTrue(s.active)
        assertEquals(2, s.count)
        s.restore(false, emptyList())
        assertFalse(s.active)
        s.restore(false, listOf("a"))
        assertTrue(s.active)
    }
}
