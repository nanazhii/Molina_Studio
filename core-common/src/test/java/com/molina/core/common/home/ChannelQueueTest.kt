package com.molina.core.common.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelQueueTest {
    private fun url(n: Any) = "https" + "://" + "tv.example/$n.m3u8"
    private fun ch(n: Int) = Channel("K$n", url(n))
    private val list = (1..5).map { ch(it) }

    @Test fun terpilihPertamaLaluBerputar() {
        val q = ChannelQueue.urls(list, list[2])
        assertEquals(listOf(3, 4, 5, 1, 2).map { url(it) }, q)
    }

    @Test fun filterKebijakanMembuangEntri() {
        val q = ChannelQueue.urls(list, list[0]) { !it.contains("/3.") }
        assertEquals(4, q.size)
        assertTrue(q.none { it.contains("/3.") })
    }

    @Test fun terpilihDitolakKosong() {
        assertEquals(emptyList<String>(), ChannelQueue.urls(list, list[1]) { !it.contains("/2.") })
    }

    @Test fun terpilihDiLuarDaftarHanyaDirinya() {
        assertEquals(listOf(url(9)), ChannelQueue.urls(list, ch(9)))
    }

    @Test fun duplikatDanBarisBaruDibuang() {
        val dirty = listOf(ch(1), ch(1), Channel("X", "https" + "://" + "tv.example/a\nb"), ch(2))
        assertEquals(2, ChannelQueue.urls(dirty, dirty[0]).size)
    }

    @Test fun dibatasiSeribu() {
        val big = (1..1500).map { ch(it) }
        assertEquals(ChannelQueue.MAX_ENTRIES, ChannelQueue.urls(big, big[700]).size)
        assertEquals(url(701), ChannelQueue.urls(big, big[700])[0])
    }
}
