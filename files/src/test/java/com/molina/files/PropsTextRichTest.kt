// molina-27u
package com.molina.files

import com.molina.core.storage.FileProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class PropsTextRichTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val view = PropsText.View("Internal", "/storage/emulated/0")

    private fun p(name: String, path: String = name, dir: Boolean = false, exec: Boolean = false, hidden: Int = 0, link: String? = null) =
        FileProps(name, path, dir, 10, 0, 0, 0L, true, true, link != null, exec, link, hidden)

    @Test fun ekstensiDanMime() {
        assertEquals("mkv", PropsText.extension("Film.MKV"))
        assertEquals("", PropsText.extension(".bashrc"))
        assertEquals("", PropsText.extension("README"))
        assertEquals("video/x-matroska", PropsText.mime("a.mkv"))
        assertEquals(null, PropsText.mime("a.xyz"))
    }

    @Test fun berkasKaya() {
        val lines = PropsText.body(listOf(p("a.mkv", "Movies/a.mkv")), utc, view).lines()
        assertEquals("Jenis: Berkas MKV", lines[0])
        assertEquals("Tipe: video/x-matroska", lines[1])
        assertTrue(lines.contains("Akses: baca, tulis"))
        assertTrue(lines.contains("Tersembunyi: tidak"))
        assertTrue(lines.contains("Lokasi: Internal/Movies"))
        assertEquals("Path: /storage/emulated/0/Movies/a.mkv", lines.last())
    }

    @Test fun skripBisaDijalankanDanTersembunyi() {
        val body = PropsText.body(listOf(p(".run.sh", exec = true)), utc, view)
        assertTrue(body.contains("Akses: baca, tulis, jalankan"))
        assertTrue(body.contains("Tersembunyi: ya"))
    }

    @Test fun folderMenyebutTersembunyiDanTautan() {
        val d = PropsText.body(listOf(p("Docs", dir = true, hidden = 3)), utc, view)
        assertTrue(d.contains("(3 tersembunyi)"))
        val l = PropsText.body(listOf(p("ln", link = "/data/x")), utc, view)
        assertTrue(l.contains("Jenis: Tautan simbolik"))
        assertTrue(l.contains("Target tautan: /data/x"))
    }

    @Test fun rootInternal() {
        assertEquals("Root Internal", PropsText.location("", "Internal"))
        assertEquals("Internal/x", PropsText.location("x/a", "Internal"))
        assertEquals("Internal", PropsText.title(listOf(p("", "", dir = true)), "Internal"))
    }

    @Test fun banyakItemSatuLokasi() {
        val body = PropsText.body(listOf(p("a", "Movies/a"), p("b", "Movies/b", dir = true)), utc, view)
        assertTrue(body.startsWith("Item: 2 (1 berkas, 1 folder)"))
        assertTrue(body.contains("Lokasi: Internal/Movies"))
    }
}
