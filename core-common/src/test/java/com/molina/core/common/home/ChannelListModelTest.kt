package com.molina.core.common.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelListModelTest {
    private fun model(n: Int = 26) = ChannelListModel(
        (1..n).map { Channel("Kanal $it", "https" + "://" + "tv.example/$it.m3u8", if (it % 2 == 0) "Berita" else "Hiburan") }
    )

    private fun numbers(m: ChannelListModel) = m.rows.map { it.number }

    @Test fun awal() {
        val m = model()
        assertEquals(26, m.rows.size)
        assertEquals(1, m.selected)
        assertEquals("001/026", m.counter())
    }

    @Test fun kosong() {
        val m = ChannelListModel(emptyList())
        assertEquals(0, m.selected)
        assertEquals("000/000", m.counter())
        assertEquals(-1, m.page(10))
        assertNull(m.pick())
    }

    @Test fun saringTetapMenjagaNomor() {
        val m = model()
        m.setQuery("berita")
        assertEquals(13, m.rows.size)
        assertEquals(listOf(2, 4, 6), numbers(m).take(3))
        assertEquals(2, m.selected)
    }

    @Test fun angkaMelompatTanpaMenyaring() {
        val m = model()
        m.setQuery("12")
        assertEquals(26, m.rows.size)
        assertEquals(12, m.selected)
        assertEquals(11, m.pick())
    }

    @Test fun angkaDiLuarDaftarTidakDipilih() {
        val m = model()
        m.setQuery("99")
        assertEquals(1, m.selected)
        assertNull(m.pick())
    }

    @Test fun pageBerjalanSepuluhDanTertahanDiUjung() {
        val m = model()
        assertEquals(10, m.page(ChannelListModel.PAGE_SIZE))
        assertEquals(11, m.selected)
        m.page(ChannelListModel.PAGE_SIZE)
        m.page(ChannelListModel.PAGE_SIZE)
        assertEquals(26, m.selected)
        m.page(-ChannelListModel.PAGE_SIZE * 5)
        assertEquals(1, m.selected)
    }

    @Test fun pickSatuBarisTersaring() {
        val m = model()
        m.setQuery("kanal 26")
        assertEquals(1, m.rows.size)
        assertEquals(25, m.pick())
    }

    @Test fun pickBanyakBarisTidakPasti() {
        val m = model()
        m.setQuery("kanal")
        assertNull(m.pick())
    }

    @Test fun selectHanyaBarisYangTampil() {
        val m = model()
        m.setQuery("berita")
        assertFalse(m.select(3))
        assertTrue(m.select(4))
        assertEquals(4, m.selected)
    }
}
