package com.molina.filemanager

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DrawerFsCountTest {
    @Test fun listMengisiJumlahIsiFolderTanpaFolderInternal() {
        val root = Files.createTempDirectory("fm-count").toFile()
        try {
            val a = File(root, "a").apply { mkdir() }
            File(a, "x.txt").writeText("x")
            File(a, "y.txt").writeText("y")
            File(a, ".molina-trash").mkdir() // internal: tidak dihitung
            File(root, "kosong").mkdir()
            File(root, "f.txt").writeText("abc")
            val byName = DrawerFs.list(root)!!.associateBy { it.name }
            assertEquals(2, byName.getValue("a").childCount)
            assertEquals(0, byName.getValue("kosong").childCount)
            assertEquals(-1, byName.getValue("f.txt").childCount) // berkas: tidak ada jumlah isi
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun countVisibleFolderTakAdaDikembalikanMinusSatu() {
        assertEquals(-1, DrawerFs.countVisible(File("/tidak/ada/folder")))
    }
}
