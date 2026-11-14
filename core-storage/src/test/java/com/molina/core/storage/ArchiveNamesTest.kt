// molina-patch26k
package com.molina.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class ArchiveNamesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun deteksiFormat() {
        assertEquals(ArchiveFormat.TAR_GZ, ArchiveNames.detect("a.tar.gz"))
        assertEquals(ArchiveFormat.TAR_GZ, ArchiveNames.detect("a.tgz"))
        assertEquals(ArchiveFormat.ZIP, ArchiveNames.detect("FOTO.ZIP"))
        assertEquals(ArchiveFormat.GZ, ArchiveNames.detect("notes.txt.gz"))
        assertEquals(ArchiveFormat.SEVEN_Z, ArchiveNames.detect("a.7z"))
        assertNull(ArchiveNames.detect(".zip"))
        assertNull(ArchiveNames.detect("a.txt"))
    }

    @Test fun stemDanNamaFolder() {
        assertEquals("a", ArchiveNames.stem("a.tar.gz"))
        assertEquals("notes.txt", ArchiveNames.stem("notes.txt.gz"))
        assertEquals("notes", ArchiveNames.folderName("notes.txt.gz"))
        assertEquals("data.v2", ArchiveNames.folderName("data.v2.zip"))
    }

    @Test fun cleanEntryMenolakTidakAman() {
        assertEquals("a/b", ArchiveNames.cleanEntry("a/b"))
        assertEquals("a/b/c", ArchiveNames.cleanEntry("/a//b/./c"))
        assertEquals("a/b", ArchiveNames.cleanEntry("a\\b"))
        assertEquals("", ArchiveNames.cleanEntry("./"))
        assertNull(ArchiveNames.cleanEntry("../x"))
        assertNull(ArchiveNames.cleanEntry("a/../b"))
        assertNull(ArchiveNames.cleanEntry("a\\..\\b"))
        assertNull(ArchiveNames.cleanEntry("a\u0000b"))
    }

    @Test fun batasNamaDihitungDalamByte() {
        assertTrue(ArchiveNames.segmentTooLong("a".repeat(256)))
        assertFalse(ArchiveNames.segmentTooLong("a".repeat(255)))
        assertTrue(ArchiveNames.segmentTooLong("\u00e9".repeat(128))) // 128 karakter = 256 byte
        assertFalse(ArchiveNames.segmentTooLong("\u00e9".repeat(127)))
        assertFalse(ArchiveNames.segmentTooLong(("a".repeat(100) + "/").repeat(5) + "x"))
    }

    @Test fun pilihanEntri() {
        assertTrue(ArchiveNames.selected("a.txt", false, null))
        val only = setOf("sub/", "a.txt")
        assertTrue(ArchiveNames.selected("a.txt", false, only))
        assertTrue(ArchiveNames.selected("sub", true, only))
        assertTrue(ArchiveNames.selected("sub/b.txt", false, only))
        assertFalse(ArchiveNames.selected("subx/b.txt", false, only))
        assertFalse(ArchiveNames.selected("c.txt", false, only))
    }

    @Test fun safeTargetMenolakKeluarDariFolder() {
        val base = tmp.newFolder("base")
        assertNotNull(ArchiveNames.safeTarget(base, "a/b.txt"))
        val outside = tmp.newFolder("luar")
        Files.createSymbolicLink(File(base, "tautan").toPath(), outside.toPath())
        assertNull(ArchiveNames.safeTarget(base, "tautan/x.txt"))
    }

    @Test fun namaUnikFolderDanKeluaran() {
        File(tmp.root, "data.v2").mkdir()
        assertEquals("data.v2 (1)", ArchiveNames.uniqueFolder(tmp.root, "data.v2").name)
        File(tmp.root, "paket.tar.gz").createNewFile()
        assertEquals("paket (1).tar.gz", ArchiveNames.uniqueOutput(tmp.root, "paket", ".tar.gz").name)
        assertEquals("lain.tar.gz", ArchiveNames.uniqueOutput(tmp.root, "lain", ".tar.gz").name)
    }

    @Test fun namaDasarKeluaran() {
        assertEquals("a", ArchiveNames.cleanBaseName(" a.ZIP ", ArchiveFormat.ZIP))
        assertEquals("a.zip", ArchiveNames.cleanBaseName("a.zip", ArchiveFormat.SEVEN_Z))
        assertNull(ArchiveNames.baseNameProblem("paket", ArchiveFormat.ZIP))
        assertEquals(ArchiveFailure.INVALID_NAME, ArchiveNames.baseNameProblem("", ArchiveFormat.ZIP))
        assertEquals(ArchiveFailure.INVALID_NAME, ArchiveNames.baseNameProblem("..", ArchiveFormat.ZIP))
        assertEquals(ArchiveFailure.INVALID_NAME, ArchiveNames.baseNameProblem("a/b", ArchiveFormat.ZIP))
        assertEquals(ArchiveFailure.INVALID_NAME, ArchiveNames.baseNameProblem("a\\b", ArchiveFormat.ZIP))
        assertEquals(ArchiveFailure.NAME_TOO_LONG, ArchiveNames.baseNameProblem("x".repeat(300), ArchiveFormat.ZIP))
        // ekstensi ikut dihitung: 252 + ".zip" = 256 byte
        assertEquals(ArchiveFailure.NAME_TOO_LONG, ArchiveNames.baseNameProblem("x".repeat(252), ArchiveFormat.ZIP))
        assertNull(ArchiveNames.baseNameProblem("x".repeat(251), ArchiveFormat.ZIP))
    }

    @Test fun namaDasarBawaan() {
        assertEquals("Film", ArchiveNames.defaultBaseName("Film", true, "Cinema"))
        assertEquals("catatan", ArchiveNames.defaultBaseName("catatan.txt", false, "Cinema"))
        assertEquals("Cinema", ArchiveNames.defaultBaseName(null, false, "Cinema"))
        assertEquals("Arsip", ArchiveNames.defaultBaseName(null, false, ""))
    }

    @Test fun levelDibatasiPerFormat() {
        assertEquals(6, ArchiveFormat.SEVEN_Z.clampLevel(9))
        assertEquals(1, ArchiveFormat.TAR_GZ.clampLevel(0))
        assertEquals(0, ArchiveFormat.TAR.clampLevel(5))
        assertTrue(ArchiveFormat.ZIP.canEncrypt)
        assertFalse(ArchiveFormat.SEVEN_Z.canEncrypt)
        assertFalse(ArchiveFormat.GZ.canCompress)
    }
}
