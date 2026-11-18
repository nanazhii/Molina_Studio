package com.molina.core.common.stream

import com.molina.core.common.StreamSearchItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchListModelTest {
    private fun u(rest: String) = "https" + "://" + rest

    private fun model(n: Int = 12) = SearchListModel(
        (1..n).map {
            SearchCard.of(
                StreamSearchItem(u("www.youtube.com/watch?v=$it"), "Judul $it", if (it % 2 == 0) "Kanal Genap" else "Kanal Ganjil")
            )
        }
    )

    private fun numbers(m: SearchListModel) = m.rows.map { it.number }

    @Test fun awal() {
        val m = model()
        assertEquals(12, m.rows.size)
        assertEquals(1, m.selected)
        assertEquals("001/012", m.counter())
    }

    @Test fun nomorTetapSaatDisaring() {
        val m = model()
        m.setQuery("judul 1")
        assertEquals(listOf(1, 10, 11, 12), numbers(m))
        assertEquals(1, m.selected)
    }

    @Test fun saringMencocokkanMeta() {
        val m = model()
        m.setQuery("genap")
        assertEquals(listOf(2, 4, 6, 8, 10, 12), numbers(m))
        assertEquals(2, m.selected)
    }

    @Test fun sorotanPindahBilaTersaring() {
        val m = model()
        assertTrue(m.select(5))
        m.setQuery("judul 1")
        assertEquals(1, m.selected)
    }

    @Test fun lompatKeNomor() {
        val m = model()
        m.setQuery("7")
        assertEquals(12, m.rows.size)
        assertEquals(7, m.selected)
        assertEquals("007/012", m.counter())
    }

    @Test fun nomorDiLuarRentangTidakMengubah() {
        val m = model()
        m.setQuery("99")
        assertEquals(1, m.selected)
        assertNull(m.pick())
        m.setQuery("0")
        assertNull(m.pick())
    }

    @Test fun paging() {
        val m = model()
        assertEquals(5, m.page(5))
        assertEquals(6, m.selected)
        assertEquals(10, m.page(5))
        assertEquals(11, m.selected)
        assertEquals(11, m.page(5))
        assertEquals(12, m.selected)
        assertEquals(0, m.page(-100))
        assertEquals(1, m.selected)
    }

    @Test fun pickDariNomor() {
        val m = model()
        m.setQuery("7")
        assertEquals(6, m.pick())
    }

    @Test fun pickSatuBaris() {
        val m = model()
        m.setQuery("judul 12")
        assertEquals(listOf(12), numbers(m))
        assertEquals(11, m.pick())
    }

    @Test fun pickMemilihSorotan() {
        val m = model()
        m.setQuery("judul 1")
        assertTrue(m.select(11))
        assertEquals(10, m.pick())
    }

    @Test fun selectTidakValid() {
        val m = model()
        assertFalse(m.select(99))
        m.setQuery("judul 12")
        assertFalse(m.select(1))
        assertEquals(12, m.selected)
    }

    @Test fun hasilKosongDanPulih() {
        val m = model()
        m.setQuery("zzz")
        assertTrue(m.rows.isEmpty())
        assertEquals(0, m.selected)
        assertEquals("000/012", m.counter())
        assertNull(m.pick())
        assertEquals(-1, m.page(1))
        m.setQuery("")
        assertEquals(12, m.rows.size)
        assertEquals(1, m.selected)
    }

    @Test fun daftarKosong() {
        val m = SearchListModel(emptyList())
        assertEquals("000/000", m.counter())
        assertNull(m.pick())
        assertEquals(-1, m.indexOfSelected())
    }
}
