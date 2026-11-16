// molina-27k
package com.molina.filemanager

import com.molina.filemanager.EmbeddedMenu.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class Embedded27kTest {
    private fun header(showHidden: Boolean = false) = EmbeddedMenu.build(
        EmbeddedMenu.State(
            targetCount = 0, hasActions = true, favoriteAvailable = true, isFavorite = false,
            trashAvailable = true, global = true, showHidden = showHidden,
        ),
    )

    private fun e(name: String, dir: Boolean = false, size: Long = 0, mod: Long = 0) =
        DrawerEntry(File("/r/$name"), dir, size, mod)

    private fun sorted(l: List<DrawerEntry>, s: DrawerSort) = l.sortedWith(DrawerSort.comparator(s)).map { it.name }

    @Test fun menuHeaderPunyaCariUrutDanTersembunyi() {
        val a = header().map { it.action }
        assertEquals(Action.REFRESH, a.first())
        assertTrue(Action.SEARCH in a && Action.SORT in a && Action.TOGGLE_HIDDEN in a)
        assertEquals("Tampilkan file tersembunyi", header(false).first { it.action == Action.TOGGLE_HIDDEN }.label)
        assertEquals("Sembunyikan file tersembunyi", header(true).first { it.action == Action.TOGGLE_HIDDEN }.label)
    }

    @Test fun pintasanLokasiSebelumFavoritDanKelompokAkhirUtuh() {
        val a = header().map { it.action }
        assertEquals(listOf(Action.FAVORITES_LIST, Action.GO_INTERNAL, Action.GO_DEBIAN, Action.OPEN_TRASH), a.takeLast(4))
        val i = a.indexOf(Action.GO_MOLINA)
        assertEquals(listOf(Action.GO_MOLINA, Action.GO_DOWNLOAD, Action.GO_DCIM, Action.GO_DOCUMENTS), a.subList(i, i + 4))
        assertTrue(a.indexOf(Action.FAVORITES_LIST) > a.indexOf(Action.GO_DOCUMENTS))
    }

    @Test fun menuBarisDanModePilihTidakMemuatAlatTampilan() {
        val row = EmbeddedMenu.build(EmbeddedMenu.State(1, true, true, false, true, global = false)).map { it.action }
        val sel = EmbeddedMenu.build(EmbeddedMenu.State(2, true, false, false, true, global = true, selecting = true)).map { it.action }
        for (x in listOf(Action.SEARCH, Action.SORT, Action.TOGGLE_HIDDEN, Action.GO_MOLINA, Action.GO_DOWNLOAD)) {
            assertFalse(x in row)
            assertFalse(x in sel)
        }
    }

    @Test fun folderSelaluDiAtasUntukSemuaUrutan() {
        val l = listOf(e("b.txt", size = 5), e("z", dir = true), e("a.txt", size = 9), e("c", dir = true))
        for (s in DrawerSort.values()) {
            assertEquals(listOf(true, true, false, false), l.sortedWith(DrawerSort.comparator(s)).map { it.isDir })
        }
    }

    @Test fun urutUkuranDanTanggal() {
        val l = listOf(e("a", size = 1, mod = 30), e("b", size = 9, mod = 10), e("c", size = 5, mod = 20))
        assertEquals(listOf("b", "c", "a"), sorted(l, DrawerSort.SIZE_DESC))
        assertEquals(listOf("a", "c", "b"), sorted(l, DrawerSort.SIZE_ASC))
        assertEquals(listOf("a", "c", "b"), sorted(l, DrawerSort.DATE_DESC))
        assertEquals(listOf("b", "c", "a"), sorted(l, DrawerSort.DATE_ASC))
    }

    @Test fun urutNamaTerbalikDanJenis() {
        val l = listOf(e("b.txt"), e("A.mp4"), e("c.apk"))
        assertEquals(listOf("c.apk", "b.txt", "A.mp4"), sorted(l, DrawerSort.NAME_DESC))
        assertEquals(listOf("c.apk", "A.mp4", "b.txt"), sorted(l, DrawerSort.TYPE_ASC))
    }

    @Test fun flattenMenyembunyikanFileTitikBilaDiminta() {
        val st = FileDrawerState()
        st.children["/r"] = listOf(DrawerEntry(File("/r/.git"), true), DrawerEntry(File("/r/a"), false))
        assertEquals(listOf(".git", "a"), DrawerFs.flatten(st, File("/r")).map { it.entry.name })
        st.showHidden = false
        assertEquals(listOf("a"), DrawerFs.flatten(st, File("/r")).map { it.entry.name })
    }

    @Test fun cariMenemukanBertingkatTanpaBedaHuruf() {
        val d = Files.createTempDirectory("fm-search").toFile()
        try {
            File(d, "Film/Seri").mkdirs()
            File(d, "Film/Seri/Naruto.mkv").writeText("x")
            File(d, "naruto.txt").writeText("x")
            File(d, ".tersembunyi").mkdir()
            File(d, ".tersembunyi/naruto-rahasia.txt").writeText("x")
            File(d, ".molina-trash").mkdir()
            File(d, ".molina-trash/naruto.bak").writeText("x")
            assertEquals(listOf("naruto.txt", "Naruto.mkv"), DrawerFs.search(d, "NARUTO", showHidden = false).map { it.name })
            val h = DrawerFs.search(d, "naruto", showHidden = true).map { it.name }
            assertTrue("naruto-rahasia.txt" in h)
            assertFalse("naruto.bak" in h)
            assertEquals(1, DrawerFs.search(d, "naruto", false, limit = 1).size)
            assertTrue(DrawerFs.search(d, "  ", false).isEmpty())
        } finally {
            d.deleteRecursively()
        }
    }

    @Test fun rumahMolinaDikenali() {
        assertTrue(DrawerFs.isMolinaHome("/storage/emulated/0/Molina"))
        assertTrue(DrawerFs.isMolinaHome("/storage/emulated/0/Molina_Studio"))
        assertFalse(DrawerFs.isMolinaHome("/storage/emulated/0/Molina/logs"))
    }
}
