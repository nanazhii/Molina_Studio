// molina-patch26k5c
package com.molina.files

import com.molina.core.storage.ArchiveEntryInfo
import com.molina.core.storage.ArchiveTree
import com.molina.core.storage.TreeSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveBrowseTest {
    private fun e(name: String, size: Long = 0L, dir: Boolean = false) = ArchiveEntryInfo(name, size, dir)

    private val tree = ArchiveTree.build(
        listOf(e("a/b/c.txt", 10), e("a/d.txt", 5), e("z.bin", 100), e("a/b/e.txt", 1)),
    )

    private fun names(b: ArchiveBrowse) = b.rows(tree).nodes.map { it.name }

    @Test fun akarMenampilkanFolderLaluBerkas() {
        assertEquals(listOf("a", "z.bin"), names(ArchiveBrowse()))
    }

    @Test fun masukFolderDanKembaliNaikSatuTingkat() {
        val a = tree.children("").first { it.name == "a" }
        val inA = ArchiveBrowse().open(a)
        assertEquals("a/", inA.folder)
        assertEquals(listOf("b", "d.txt"), names(inA))
        val up = inA.back()
        assertEquals("", up?.folder)
        assertNull(up?.back())
    }

    @Test fun membukaBerkasTidakMengubahKeadaan() {
        val z = tree.children("").first { it.name == "z.bin" }
        assertEquals(ArchiveBrowse(), ArchiveBrowse().open(z))
    }

    @Test fun kembaliMenutupPencarianLaluPilihanLaluFolder() {
        var b = ArchiveBrowse(folder = "a/b/", selected = setOf("a/b/c.txt"), query = "c")
        b = b.back()!!
        assertEquals("", b.query)
        assertTrue(b.selecting)
        b = b.back()!!
        assertFalse(b.selecting)
        assertEquals("a/b/", b.folder)
        b = b.back()!!
        assertEquals("a/", b.folder)
        b = b.back()!!
        assertEquals("", b.folder)
        assertNull(b.back())
    }

    @Test fun pencarianMencariDiSeluruhPohonDanMengosongkanPilihan() {
        val b = ArchiveBrowse(selected = setOf("z.bin")).withQuery("c.txt")
        assertFalse(b.selecting)
        assertTrue(b.searching)
        assertEquals(listOf("c.txt"), names(b))
    }

    @Test fun pencarianDipotongDiBatasDanDitandai() {
        val many = ArchiveTree.build((1..300).map { e("f$it.txt", 1) })
        val rows = ArchiveBrowse().withQuery("f").rows(many)
        assertEquals(ArchiveBrowse.SEARCH_LIMIT, rows.nodes.size)
        assertTrue(rows.capped)
        assertTrue(rows.searching)
    }

    @Test fun keyOnlyMembuangKunciDiDalamFolderYangJugaDipilih() {
        val b = ArchiveBrowse(selected = setOf("a/", "a/b/c.txt", "a/b/", "z.bin"))
        assertEquals(setOf("a/", "z.bin"), b.onlyKeys())
    }

    @Test fun keyOnlyNullBilaTidakAdaPilihan() {
        assertNull(ArchiveBrowse().onlyKeys())
    }

    @Test fun namaMiripBukanLeluhur() {
        val b = ArchiveBrowse(selected = setOf("a/", "ab/c.txt"))
        assertEquals(setOf("a/", "ab/c.txt"), b.onlyKeys())
    }

    @Test fun pilihSemuaHanyaAnakLangsungFolderAktif() {
        val b = ArchiveBrowse(folder = "a/").selectAll(tree)
        assertEquals(setOf("a/b/", "a/d.txt"), b.selected)
    }

    @Test fun toggleMenambahDanMelepasPilihan() {
        val b = ArchiveBrowse().toggle("z.bin")
        assertTrue(b.selecting)
        assertFalse(b.toggle("z.bin").selecting)
    }

    @Test fun urutUkuranMenaruhBerkasTerbesarDulu() {
        val b = ArchiveBrowse(folder = "a/b/", sort = TreeSort.SIZE)
        assertEquals(listOf("c.txt", "e.txt"), names(b))
        assertEquals(listOf("c.txt", "e.txt"), names(b.withSort(TreeSort.NAME)))
    }

    @Test fun goToMeresetPencarianDanPilihan() {
        val b = ArchiveBrowse(folder = "a/b/", query = "x", selected = setOf("k")).goTo("a/")
        assertEquals("a/", b.folder)
        assertEquals("", b.query)
        assertTrue(b.selected.isEmpty())
    }
}
