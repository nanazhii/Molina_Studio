// molina-patch26a
package com.molina.core.storage

import com.molina.core.common.ErrorKind
import com.molina.core.common.errorOrNull
import com.molina.core.common.getOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileOperationsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val none = emptySet<File>()

    @Test fun buatFolderDanBerkas() {
        val f = FileOperations.createFolder(tmp.root, "baru").getOrNull()!!
        assertTrue(f.isDirectory && File(tmp.root, "baru").isDirectory)
        val b = FileOperations.createFile(tmp.root, "skrip.sh").getOrNull()!!
        assertFalse(b.isDirectory)
        assertTrue(File(tmp.root, "skrip.sh").isFile)
    }

    @Test fun buatNamaSudahAdaDitolak() {
        File(tmp.root, "ada").mkdir()
        assertEquals(ErrorKind.ALREADY_EXISTS, FileOperations.createFolder(tmp.root, "ada").errorOrNull()?.kind)
        assertEquals(ErrorKind.ALREADY_EXISTS, FileOperations.createFile(tmp.root, "ada").errorOrNull()?.kind)
    }

    @Test fun buatNamaTidakValidDitolak() {
        assertEquals(ErrorKind.INVALID_NAME, FileOperations.createFile(tmp.root, "a/b").errorOrNull()?.kind)
    }

    @Test fun buatDiFolderYangTidakAda() {
        val missing = File(tmp.root, "tidak-ada")
        assertEquals(ErrorKind.NOT_FOUND, FileOperations.createFolder(missing, "x").errorOrNull()?.kind)
    }

    @Test fun ubahNama() {
        File(tmp.root, "lama.txt").writeText("isi")
        val e = FileOperations.rename(File(tmp.root, "lama.txt"), " baru.txt ", none).getOrNull()!!
        assertEquals("baru.txt", e.name)
        assertEquals("isi", File(tmp.root, "baru.txt").readText())
        assertFalse(File(tmp.root, "lama.txt").exists())
    }

    @Test fun ubahNamaKeNamaYangAdaDitolak() {
        File(tmp.root, "a").createNewFile()
        File(tmp.root, "b").createNewFile()
        assertEquals(ErrorKind.ALREADY_EXISTS, FileOperations.rename(File(tmp.root, "a"), "b", none).errorOrNull()?.kind)
    }

    @Test fun ubahNamaSamaTidakMelakukanApaApa() {
        File(tmp.root, "a").createNewFile()
        assertNotNull(FileOperations.rename(File(tmp.root, "a"), "a", none).getOrNull())
    }

    @Test fun ubahNamaDilindungiDitolak() {
        val logs = File(tmp.root, "logs").apply { mkdir() }
        assertEquals(ErrorKind.PROTECTED, FileOperations.rename(logs, "x", setOf(logs)).errorOrNull()?.kind)
        assertEquals(ErrorKind.PROTECTED, FileOperations.rename(tmp.root, "x", setOf(logs)).errorOrNull()?.kind)
    }

    @Test fun hapusRekursif() {
        val d = File(tmp.root, "d")
        File(d, "x/y").mkdirs()
        File(d, "x/y/f.txt").writeText("1")
        assertTrue(FileOperations.delete(d, none).getOrNull() != null)
        assertFalse(d.exists())
    }

    @Test fun hapusTidakAdaDanDilindungi() {
        assertEquals(ErrorKind.NOT_FOUND, FileOperations.delete(File(tmp.root, "x"), none).errorOrNull()?.kind)
        val logs = File(tmp.root, "logs").apply { mkdir() }
        assertEquals(ErrorKind.PROTECTED, FileOperations.delete(logs, setOf(logs)).errorOrNull()?.kind)
        assertEquals(ErrorKind.PROTECTED, FileOperations.delete(tmp.root, setOf(logs)).errorOrNull()?.kind)
        assertTrue(logs.isDirectory)
    }

    @Test fun hapusTautanSimbolikTidakMenghapusTarget() {
        val target = File(tmp.root, "asli").apply { mkdir() }
        File(target, "isi.txt").writeText("x")
        val link = File(tmp.root, "tautan")
        try {
            java.nio.file.Files.createSymbolicLink(link.toPath(), target.toPath())
        } catch (e: Exception) {
            return // sistem berkas tanpa symlink: tes dilewati
        }
        assertTrue(FileOperations.delete(link, none).getOrNull() != null)
        assertTrue(File(target, "isi.txt").exists())
    }
}
