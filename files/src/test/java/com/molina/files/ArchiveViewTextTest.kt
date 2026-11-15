// molina-patch26k5c
package com.molina.files

import com.molina.core.storage.TreeNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveViewTextTest {
    private val dot = " \u00B7 "

    @Test fun angkaDikelompokkanDenganTitik() {
        assertEquals("999", ArchiveViewText.grouped(999))
        assertEquals("50.000", ArchiveViewText.grouped(50_000))
        assertEquals("1.234.567", ArchiveViewText.grouped(1_234_567))
    }

    @Test fun barisBerkasMemuatUkuran() {
        val n = TreeNode("a.txt", "a.txt", false, 1536L, 0, 0, 1536L)
        assertEquals("1.5 KB", ArchiveViewText.nodeLine(n))
    }

    @Test fun barisFolderMemuatSubfolderBerkasDanUkuran() {
        val n = TreeNode("a", "a/", true, 0L, 3, 1, 2048L)
        assertEquals("1 folder" + dot + "3 berkas" + dot + "2.0 KB", ArchiveViewText.nodeLine(n))
        val flat = TreeNode("b", "b/", true, 0L, 2, 0, 10L)
        assertEquals("2 berkas" + dot + "10 B", ArchiveViewText.nodeLine(flat))
    }

    @Test fun hasilPencarianMenampilkanFolderInduk() {
        val deep = TreeNode("c.txt", "a/b/c.txt", false, 10L, 0, 0, 10L)
        assertEquals("/a/b/" + dot + "10 B", ArchiveViewText.hitLine(deep))
        val top = TreeNode("z.bin", "z.bin", false, 100L, 0, 0, 100L)
        assertEquals("/" + dot + "100 B", ArchiveViewText.hitLine(top))
    }

    @Test fun bannerPotongMenyebutJumlahDanKonsekuensi() {
        val t = ArchiveViewText.truncatedBanner(50_000)
        assertTrue(t, t.contains("50.000"))
        assertTrue(t, t.contains("tidak ikut pencarian"))
    }

    @Test fun cacheKosongDanTerisi() {
        assertEquals("Cache sudah kosong", ArchiveViewText.cacheCleared(0L))
        assertEquals("Cache dibersihkan (2.0 MB)", ArchiveViewText.cacheCleared(2L * 1024 * 1024))
    }

    @Test fun ringkasanIsiArsip() {
        assertEquals("1.200 berkas" + dot + "3 folder" + dot + "1.0 KB", ArchiveViewText.counts(1200, 3, 1024L))
        assertEquals("5 berkas" + dot + "10 B", ArchiveViewText.counts(5, 0, 10L))
    }
}
