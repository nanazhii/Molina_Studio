package com.molina.filemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DrawerFsTest {
    @Test fun displayPathMenulisInternal() {
        assertEquals("Internal", DrawerFs.displayPath("/storage/emulated/0"))
        assertEquals("Internal/Molina/a", DrawerFs.displayPath("/storage/emulated/0/Molina/a"))
        assertEquals("/storage/1234-ABCD/x", DrawerFs.displayPath("/storage/1234-ABCD/x"))
    }

    @Test fun shellPathProotMemetakanKeSdcard() {
        assertEquals("/sdcard", DrawerFs.shellPath(File("/storage/emulated/0"), true))
        assertEquals("/sdcard/Molina_Studio", DrawerFs.shellPath(File("/storage/emulated/0/Molina_Studio"), true))
        assertNull(DrawerFs.shellPath(File("/storage/1234-ABCD/x"), true))
        assertEquals("/storage/1234-ABCD/x", DrawerFs.shellPath(File("/storage/1234-ABCD/x"), false))
    }

    @Test fun quoteMengamankanPetikTunggal() {
        assertEquals("'abc'", DrawerFs.quote("abc"))
        assertEquals("'a'\\''b'", DrawerFs.quote("a'b"))
    }

    @Test fun validNameMenolakNamaTerlarang() {
        assertFalse(DrawerFs.validName(""))
        assertFalse(DrawerFs.validName("  "))
        assertFalse(DrawerFs.validName("."))
        assertFalse(DrawerFs.validName(".."))
        assertFalse(DrawerFs.validName("a/b"))
        assertTrue(DrawerFs.validName("a.txt"))
        assertTrue(DrawerFs.validName(".bashrc"))
    }

    @Test fun karakterKontrolTerdeteksi() {
        assertTrue(DrawerFs.hasControlChars("a\nb"))
        assertTrue(DrawerFs.hasControlChars("a\u007Fb"))
        assertFalse(DrawerFs.hasControlChars("ab c"))
    }

    @Test fun folderUtamaDanVolumeDilindungi() {
        val vols = listOf(File(DrawerFs.INTERNAL_ROOT), File("/storage/1234-ABCD"))
        assertTrue(DrawerFs.isProtected(File(DrawerFs.DEFAULT_ROOT), vols))
        assertTrue(DrawerFs.isProtected(File("/storage/emulated/0/Molina_Studio"), vols))
        assertTrue(DrawerFs.isProtected(File("/storage/1234-ABCD"), vols))
        assertFalse(DrawerFs.isProtected(File("/storage/emulated/0/Molina/x"), vols))
    }

    @Test fun volumeLabel() {
        assertEquals("Internal", DrawerFs.volumeLabel(File(DrawerFs.INTERNAL_ROOT)))
        assertEquals("1234-ABCD", DrawerFs.volumeLabel(File("/storage/1234-ABCD")))
    }

    @Test fun listUrutFolderDuluLaluNamaTanpaMembedakanHuruf() {
        val d = Files.createTempDirectory("fm-list").toFile()
        try {
            File(d, "b").mkdir(); File(d, "A").mkdir()
            File(d, "c.txt").writeText("x"); File(d, "B.txt").writeText("x")
            val names = DrawerFs.list(d)!!.map { it.name }
            assertEquals(listOf("A", "b", "B.txt", "c.txt"), names)
            assertTrue(DrawerFs.list(d)!!.first().isDir)
            assertNull(DrawerFs.list(File(d, "tidak-ada")))
        } finally { d.deleteRecursively() }
    }

    @Test fun flattenHanyaMembukaFolderYangDiperluas() {
        val st = FileDrawerState()
        val root = File("/r")
        st.children["/r"] = listOf(DrawerEntry(File("/r/a"), true), DrawerEntry(File("/r/f"), false))
        st.children["/r/a"] = listOf(DrawerEntry(File("/r/a/x"), false))
        assertEquals(listOf("a", "f"), DrawerFs.flatten(st, root).map { it.entry.name })
        st.expanded.add("/r/a")
        val rows = DrawerFs.flatten(st, root)
        assertEquals(listOf("a", "x", "f"), rows.map { it.entry.name })
        assertEquals(listOf(0, 1, 0), rows.map { it.depth })
        assertTrue(rows[0].expanded)
    }

    @Test fun deleteSafeMenghapusRekursif() {
        val d = Files.createTempDirectory("fm-del").toFile()
        File(d, "s/t").mkdirs(); File(d, "s/t/f.txt").writeText("x"); File(d, "g.txt").writeText("x")
        assertTrue(DrawerFs.deleteSafe(d))
        assertFalse(d.exists())
    }
}
