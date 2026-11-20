package com.molina.core.common.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelSourceBookTest {
    private class MemStore : ChannelSourceStore {
        var text = ""
        override fun read() = text
        override fun write(text: String) {
            this.text = text
        }
    }

    private fun u(rest: String) = "https" + "://" + rest

    @Test fun tambahMenaruhDiDepanDanMenjadiTerakhir() {
        val b = ChannelSourceBook(MemStore())
        b.add(u("a.example/x.m3u"))
        b.add("/storage/emulated/0/Molina/Media/IPTV/playlist.m3u")
        assertEquals(listOf("playlist.m3u", "a.example / x.m3u"), b.list().map { it.name })
        assertEquals("/storage/emulated/0/Molina/Media/IPTV/playlist.m3u", b.last)
    }

    @Test fun duplikatDipindahKeDepan() {
        val b = ChannelSourceBook(MemStore())
        b.add(u("a.example/1.m3u"))
        b.add(u("b.example/2.m3u"))
        b.add(u("a.example/1.m3u"))
        assertEquals(2, b.list().size)
        assertEquals(u("a.example/1.m3u"), b.list()[0].ref)
    }

    @Test fun batasSepuluh() {
        val b = ChannelSourceBook(MemStore())
        for (i in 1..12) b.add(u("h$i.example/l.m3u"))
        assertEquals(10, b.list().size)
        assertEquals(u("h12.example/l.m3u"), b.list()[0].ref)
    }

    @Test fun hapusMembersihkanTerakhir() {
        val b = ChannelSourceBook(MemStore())
        b.add(u("a.example/1.m3u"))
        b.add(u("b.example/2.m3u"))
        b.remove(u("b.example/2.m3u"))
        assertEquals(1, b.list().size)
        assertNull(b.last)
    }

    @Test fun lastHanyaUntukSumberYangAda() {
        val b = ChannelSourceBook(MemStore())
        b.add(u("a.example/1.m3u"))
        b.last = u("tidak-ada.example/x.m3u")
        assertNull(b.last)
    }

    @Test fun labelTautanTanpaTokenDanQuery() {
        assertEquals("tv.example / daftar.m3u", ChannelSourceBook.nameOf(u("user:pass@www.tv.example/a/daftar.m3u?token=RAHASIA#x")))
        assertEquals("tv.example", ChannelSourceBook.nameOf(u("tv.example/")))
        assertEquals("Playlist", ChannelSourceBook.nameOf("/"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun barisBaruDitolak() {
        ChannelSourceBook(MemStore()).add("/sdcard/a\nb.m3u")
    }
}
