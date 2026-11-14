// molina-patch26j
package com.molina.files

import com.molina.core.storage.FileProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class PropsTextTest {
    private val utc = TimeZone.getTimeZone("UTC")

    private fun p(
        name: String,
        dir: Boolean = false,
        size: Long = 0,
        files: Int = 0,
        folders: Int = 0,
        path: String = name,
        write: Boolean = true,
    ) = FileProps(name, path, dir, size, files, folders, 0L, true, write, false)

    @Test fun tanggalMemakaiPolaIndonesiaDiZonaYangDiberikan() {
        assertEquals("01/01/1970 00.00", PropsText.date(0L, utc))
    }

    @Test fun judulSatuItemDanBanyakItem() {
        assertEquals("a.mkv", PropsText.title(listOf(p("a.mkv"))))
        assertEquals("2 item", PropsText.title(listOf(p("a"), p("b"))))
        assertEquals("Molina", PropsText.title(listOf(p("", dir = true, path = ""))))
    }

    @Test fun badanBerkasTunggal() {
        val lines = PropsText.body(listOf(p("a.mkv", size = 1536, path = "Movies/a.mkv")), utc).lines()
        assertEquals("Jenis: Berkas", lines[0])
        assertTrue(lines[1].startsWith("Ukuran: 1.5 KB ("))
        assertEquals("Diubah: 01/01/1970 00.00", lines[2])
        assertEquals("Akses: baca, tulis", lines[3])
        assertEquals("Lokasi: Molina/Movies", lines[4])
    }

    @Test fun badanFolderMemuatIsi() {
        val lines = PropsText.body(listOf(p("Movies", dir = true, size = 10, files = 2, folders = 1)), utc).lines()
        assertEquals("Jenis: Folder", lines[0])
        assertEquals("Isi: 2 berkas, 1 folder", lines[2])
        assertEquals("Lokasi: Molina", lines.last())
    }

    @Test fun aksesHanyaBaca() {
        val body = PropsText.body(listOf(p("a", write = false)), utc)
        assertTrue(body.contains("Akses: baca, hanya-baca"))
    }

    @Test fun badanBanyakItemMenjumlah() {
        val items = listOf(p("a", size = 100), p("d", dir = true, size = 50, files = 2, folders = 1))
        val lines = PropsText.body(items).lines()
        assertEquals("Item: 2", lines[0])
        assertTrue(lines[1].startsWith("Ukuran total: 150 B ("))
        assertEquals("Isi: 3 berkas, 2 folder", lines[2])
    }

    @Test fun lokasiRootDanBersarang() {
        assertEquals("Root Molina", PropsText.location(""))
        assertEquals("Molina", PropsText.location("a.txt"))
        assertEquals("Molina/x/y", PropsText.location("x/y/z.txt"))
    }
}
