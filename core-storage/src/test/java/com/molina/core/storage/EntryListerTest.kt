// molina-patch26a
package com.molina.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EntryListerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file(dir: File, name: String, bytes: Int = 1, modified: Long = 1_000L): File {
        val f = File(dir, name)
        f.parentFile?.mkdirs()
        f.writeBytes(ByteArray(bytes))
        f.setLastModified(modified)
        return f
    }

    private fun names(r: ListResult?) = r!!.entries.map { it.name }

    @Test fun folderLebihDuluLaluNamaTanpaMembedakanHuruf() {
        val d = tmp.root
        File(d, "zeta").mkdir()
        File(d, "Alpha").mkdir()
        file(d, "b.txt")
        file(d, "A.txt")
        assertEquals(listOf("Alpha", "zeta", "A.txt", "b.txt"), names(EntryLister.list(d, ListOptions())))
    }

    @Test fun berkasTersembunyiDisembunyikanKecualiDiminta() {
        val d = tmp.root
        file(d, ".rahasia")
        file(d, "biasa")
        assertEquals(listOf("biasa"), names(EntryLister.list(d, ListOptions())))
        assertEquals(listOf(".rahasia", "biasa"), names(EntryLister.list(d, ListOptions(showHidden = true))))
    }

    @Test fun sortirUkuranMenurunDanFolderTetapDiAtas() {
        val d = tmp.root
        File(d, "dir").mkdir()
        file(d, "kecil", bytes = 1)
        file(d, "besar", bytes = 100)
        val r = EntryLister.list(d, ListOptions(sortKey = SortKey.SIZE, ascending = false))
        assertEquals(listOf("dir", "besar", "kecil"), names(r))
    }

    @Test fun sortirTanggalDanTipe() {
        val d = tmp.root
        file(d, "a.mp4", modified = 3_000L)
        file(d, "b.avi", modified = 1_000L)
        file(d, "c.mkv", modified = 2_000L)
        assertEquals(listOf("b.avi", "c.mkv", "a.mp4"), names(EntryLister.list(d, ListOptions(sortKey = SortKey.DATE))))
        assertEquals(listOf("b.avi", "c.mkv", "a.mp4"), names(EntryLister.list(d, ListOptions(sortKey = SortKey.TYPE))))
    }

    @Test fun kueriSemuaKataHarusCocokSatuFolder() {
        val d = tmp.root
        file(d, "Film Aksi 2020.mkv")
        file(d, "Film Drama.mkv")
        file(d, "catatan.txt")
        assertEquals(listOf("Film Aksi 2020.mkv"), names(EntryLister.list(d, ListOptions(query = "film AKSI"))))
        assertEquals(2, EntryLister.list(d, ListOptions(query = "film"))!!.entries.size)
    }

    @Test fun pencarianDalamMengisiRelDir() {
        val d = tmp.root
        file(d, "a/b/target.mp4")
        file(d, "target.txt")
        val r = EntryLister.list(d, ListOptions(query = "target", deep = true))!!
        assertFalse(r.truncated)
        val byName = r.entries.associateBy { it.name }
        assertEquals("a/b", byName["target.mp4"]!!.relDir)
        assertEquals(null, byName["target.txt"]!!.relDir)
    }

    @Test fun filterEkstensiHanyaBerkasFolderTetapTampil() {
        val d = tmp.root
        File(d, "folder").mkdir()
        file(d, "a.mp4")
        file(d, "b.txt")
        val r = EntryLister.list(d, ListOptions(onlyExt = setOf("mp4")))
        assertEquals(listOf("folder", "a.mp4"), names(r))
    }

    @Test fun dibatalkanMengembalikanHasilSejauhIni() {
        val d = tmp.root
        file(d, "x/target")
        val r = EntryLister.list(d, ListOptions(query = "target", deep = true)) { true }!!
        assertTrue(r.entries.isEmpty())
    }

    @Test fun folderTidakBisaDibacaMengembalikanNull() {
        val f = file(tmp.root, "bukan-folder")
        assertEquals(null, EntryLister.list(f, ListOptions()))
    }

    @Test fun batasHasilMemotong() {
        val d = tmp.root
        for (i in 0 until EntryLister.MAX_RESULTS + 5) File(d, "n$i").createNewFile()
        val r = EntryLister.list(d, ListOptions(query = "n", deep = true))!!
        assertTrue(r.truncated)
        assertEquals(EntryLister.MAX_RESULTS, r.entries.size)
    }
}
