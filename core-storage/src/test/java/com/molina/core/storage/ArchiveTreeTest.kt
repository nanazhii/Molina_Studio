// molina-patch26k5a
package com.molina.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveTreeTest {
    private fun e(name: String, size: Long = 0L, dir: Boolean = false) = ArchiveEntryInfo(name, size, dir)

    @Test fun folderImplisitDisintesisDariJalurBerkas() {
        val t = ArchiveTree.build(listOf(e("a/b/c.txt", 10)))
        val root = t.children("")
        assertEquals(listOf("a"), root.map { it.name })
        assertTrue(root[0].isDirectory)
        assertEquals("a/", root[0].path)
        assertEquals(listOf("b"), t.children("a/").map { it.name })
        val leaf = t.children("a/b/").single()
        assertEquals("c.txt", leaf.name)
        assertEquals("a/b/c.txt", leaf.path)
        assertEquals(10L, leaf.size)
        assertEquals(1, t.totalFiles)
        assertEquals(2, t.totalFolders)
        assertEquals(10L, t.totalBytes)
        val a = root[0]
        assertEquals(1, a.files)
        assertEquals(1, a.folders)
        assertEquals(10L, a.bytes)
    }

    @Test fun urutanNamaFolderDuluTanpaMembedakanHuruf() {
        val t = ArchiveTree.build(listOf(e("b.txt"), e("A.txt"), e("z", dir = true), e("Apple/x")))
        assertEquals(listOf("Apple", "z", "A.txt", "b.txt"), t.children("").map { it.name })
    }

    @Test fun urutanUkuranFolderDuluLaluBerkasTerbesar() {
        val t = ArchiveTree.build(listOf(e("small.bin", 1), e("big.bin", 100), e("mid.bin", 50), e("d/x", 1)))
        assertEquals(listOf("d", "big.bin", "mid.bin", "small.bin"), t.children("", TreeSort.SIZE).map { it.name })
    }

    @Test fun hitungIsiFolderRekursif() {
        val t = ArchiveTree.build(listOf(e("d/a.txt", 5), e("d/s/b.txt", 7), e("d/s", dir = true)))
        val d = t.children("").single()
        assertEquals(2, d.files)
        assertEquals(1, d.folders)
        assertEquals(12L, d.bytes)
        val s = t.children("d/").first { it.isDirectory }
        assertEquals(1, s.files)
        assertEquals(0, s.folders)
        assertEquals(7L, s.bytes)
    }

    @Test fun entriFolderEksplisitTidakGanda() {
        val t = ArchiveTree.build(listOf(e("d", dir = true), e("d/f", 1)))
        assertEquals(1, t.children("").size)
        assertEquals(1, t.totalFolders)
        assertEquals(1, t.totalFiles)
    }

    @Test fun berkasDanFolderBernamaSamaKeduanyaAda() {
        val t = ArchiveTree.build(listOf(e("a", 3), e("a/b.txt", 1)))
        val root = t.children("")
        assertEquals(listOf("a/", "a"), root.map { it.path })
        assertTrue(root[0].isDirectory)
        assertFalse(root[1].isDirectory)
    }

    @Test fun entriTidakAmanDilewatiDanDinormalkan() {
        val t = ArchiveTree.build(listOf(e("../x"), e("a/../b"), e("ok.txt", 1), e("w\\f.txt", 2), e("./"), e("")))
        assertEquals(2, t.skipped)
        assertEquals(listOf("w", "ok.txt"), t.children("").map { it.name })
        assertEquals("w/f.txt", t.children("w/").single().path)
    }

    @Test fun berkasKembarYangTerakhirMenang() {
        val t = ArchiveTree.build(listOf(e("a.txt", 1), e("a.txt", 2)))
        assertEquals(1, t.duplicates)
        assertEquals(1, t.children("").size)
        assertEquals(2L, t.children("").single().size)
        assertEquals(2L, t.totalBytes)
    }

    @Test fun ukuranTidakDiketahuiDihitungNol() {
        val t = ArchiveTree.build(listOf(e("a.bin", -1)))
        assertEquals(0L, t.children("").single().size)
        assertEquals(0L, t.totalBytes)
    }

    @Test fun pencarianNamaTanpaMembedakanHuruf() {
        val t = ArchiveTree.build(listOf(e("Film/Sub/Indonesia.srt", 1), e("Film/Video.mkv", 1000), e("readme.TXT", 1)))
        assertEquals(listOf("Film/Sub/Indonesia.srt"), t.search("srt", 10).hits.map { it.path })
        assertEquals(listOf("Film/Sub/"), t.search("SUB", 10).hits.map { it.path })
        assertEquals(listOf("readme.TXT"), t.search("txt", 10).hits.map { it.path })
        assertTrue(t.search("", 10).hits.isEmpty())
        assertTrue(t.search("  ", 10).hits.isEmpty())
    }

    @Test fun pencarianDibatasiDanMenandaiCapped() {
        val t = ArchiveTree.build((1..5).map { e("f$it.txt") })
        val r = t.search("f", 3)
        assertEquals(3, r.hits.size)
        assertTrue(r.capped)
        assertFalse(t.search("f", 5).capped)
    }

    @Test fun breadcrumbDanInduk() {
        val t = ArchiveTree.build(listOf(e("a/b/c.txt")))
        assertEquals(listOf("" to "", "a" to "a/", "b" to "a/b/"), t.breadcrumbs("a/b/").map { it.name to it.path })
        assertEquals(listOf("" to ""), t.breadcrumbs("").map { it.name to it.path })
        assertEquals("a/b/", ArchiveTree.parent("a/b/c.txt"))
        assertEquals("a/", ArchiveTree.parent("a/b/"))
        assertEquals("", ArchiveTree.parent("a/"))
        assertEquals("", ArchiveTree.parent("x"))
    }

    @Test fun kunciSimpulCocokDenganParameterOnlyEkstrak() {
        val t = ArchiveTree.build(listOf(e("a/b/c.txt", 1), e("x.txt", 1)))
        val folderKey = t.children("").first { it.isDirectory }.path
        assertTrue(ArchiveNames.selected("a/b/c.txt", false, setOf(folderKey)))
        assertFalse(ArchiveNames.selected("x.txt", false, setOf(folderKey)))
        assertTrue(ArchiveNames.selected("x.txt", false, setOf("x.txt")))
    }

    @Test fun isFolderUntukAkarFolderDanBerkas() {
        val t = ArchiveTree.build(listOf(e("a/b/c.txt")))
        assertTrue(t.isFolder(""))
        assertTrue(t.isFolder("a/"))
        assertFalse(t.isFolder("a/b/c.txt"))
        assertFalse(t.isFolder("zzz/"))
    }

    @Test fun daftarKosong() {
        val t = ArchiveTree.build(emptyList())
        assertTrue(t.children("").isEmpty())
        assertEquals(0, t.totalFiles)
        assertEquals(0, t.totalFolders)
        assertEquals(0L, t.totalBytes)
    }

    @Test fun duaPuluhRibuEntriDalamSatuFolder() {
        val t = ArchiveTree.build((1..20_000).map { e("d/f$it.bin", it.toLong()) })
        assertEquals(20_000, t.children("d/").size)
        assertEquals(20_000, t.children("").single().files)
        assertEquals(20_000L * 20_001L / 2L, t.totalBytes)
    }
}
