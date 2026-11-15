// molina-27g2
package com.molina.filemanager

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerSortTest {
    private fun f(n: String, size: Long = 0, mod: Long = 0) = DrawerEntry(File("/r/$n"), false, size, mod)
    private fun d(n: String, mod: Long = 0) = DrawerEntry(File("/r/$n"), true, 0, mod)
    private fun names(l: List<DrawerEntry>) = l.map { it.name }

    @Test fun nameAscFoldersFirstCaseInsensitive() {
        val l = listOf(f("b.txt"), d("Zed"), f("A.txt"), d("alpha"))
        assertEquals(listOf("alpha", "Zed", "A.txt", "b.txt"), names(DrawerSort.sorted(l, DrawerSort.DEFAULT)))
    }

    @Test fun nameDescKeepsFoldersOnTop() {
        val l = listOf(f("a"), d("x"), d("y"), f("b"))
        assertEquals(listOf("y", "x", "b", "a"), names(DrawerSort.sorted(l, DrawerSort(DrawerSortKey.NAME, false))))
    }

    @Test fun sizeDescThenNameOnTie() {
        val l = listOf(f("s", 5), f("b", 50), f("a", 50), d("dir"))
        assertEquals(listOf("dir", "a", "b", "s"), names(DrawerSort.sorted(l, DrawerSort(DrawerSortKey.SIZE, false))))
    }

    @Test fun dateNewestFirstAndFoldersByDate() {
        val l = listOf(d("old", 1), d("new", 9), f("x", 0, 3), f("y", 0, 7))
        assertEquals(listOf("new", "old", "y", "x"), names(DrawerSort.sorted(l, DrawerSort(DrawerSortKey.DATE, false))))
    }

    @Test fun typeGroupsByExtensionDotfileHasNone() {
        val l = listOf(f("b.txt"), f("a.PY"), f(".bashrc"), f("c.py"), f("noext"))
        assertEquals(listOf(".bashrc", "noext", "a.PY", "c.py", "b.txt"), names(DrawerSort.sorted(l, DrawerSort(DrawerSortKey.TYPE, true))))
    }

    @Test fun chooseTogglesActiveAndResetsOthers() {
        val s = DrawerSort.DEFAULT.choose(DrawerSortKey.NAME)
        assertEquals(DrawerSort(DrawerSortKey.NAME, false), s)
        assertEquals(DrawerSort(DrawerSortKey.DATE, false), s.choose(DrawerSortKey.DATE))
        assertEquals(DrawerSort(DrawerSortKey.TYPE, true), s.choose(DrawerSortKey.TYPE))
    }

    @Test fun encodeDecodeRoundTripAndBadInput() {
        for (k in DrawerSortKey.values()) for (a in listOf(true, false)) {
            assertEquals(DrawerSort(k, a), DrawerSort.decode(DrawerSort(k, a).encode()))
        }
        for (bad in listOf(null, "", "NAME", "X:asc", "NAME:up", "NAME:asc:x")) assertEquals(DrawerSort.DEFAULT, DrawerSort.decode(bad))
    }

    @Test fun flattenAppliesSortPerLevel() {
        val st = FileDrawerState()
        st.children["/r"] = listOf(d("a"), f("m", 1), f("n", 9))
        st.children["/r/a"] = listOf(f("p", 2), f("q", 8))
        st.expanded.add("/r/a")
        val rows = DrawerFs.flatten(st, File("/r"), DrawerSort(DrawerSortKey.SIZE, false))
        assertEquals(listOf("a", "q", "p", "n", "m"), rows.map { it.entry.name })
    }

    @Test fun labelShowsKeyAndArrow() {
        assertEquals("Nama \u25B2", DrawerSort.DEFAULT.label)
        assertEquals("Tanggal \u25BC", DrawerSort(DrawerSortKey.DATE, false).label)
    }
}
