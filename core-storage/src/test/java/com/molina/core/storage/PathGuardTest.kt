package com.molina.core.storage

import com.molina.core.common.ErrorKind
import com.molina.core.common.errorOrNull
import com.molina.core.common.getOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PathGuardTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun relatifDiDalamRootDiterima() {
        val root = tmp.newFolder("root")
        val r = PathGuard.resolveInside(root, "a/b")
        assertEquals(File(root.canonicalFile, "a/b"), r.getOrNull())
    }

    @Test fun stringKosongAdalahRoot() {
        val root = tmp.newFolder("root")
        assertEquals(root.canonicalFile, PathGuard.resolveInside(root, "").getOrNull())
    }

    @Test fun duaTitikKeluarRootDitolak() {
        val root = tmp.newFolder("root")
        assertEquals(ErrorKind.INVALID_PATH, PathGuard.resolveInside(root, "../luar").errorOrNull()?.kind)
        assertEquals(ErrorKind.INVALID_PATH, PathGuard.resolveInside(root, "a/../../luar").errorOrNull()?.kind)
    }

    @Test fun namaSerupaTidakLolos() {
        val root = tmp.newFolder("root")
        tmp.newFolder("root-lain")
        assertEquals(ErrorKind.INVALID_PATH, PathGuard.resolveInside(root, "../root-lain").errorOrNull()?.kind)
    }

    @Test fun symlinkKeluarRootDitolak() {
        val root = tmp.newFolder("root")
        val luar = tmp.newFolder("luar")
        java.nio.file.Files.createSymbolicLink(File(root, "tautan").toPath(), luar.toPath())
        assertEquals(ErrorKind.INVALID_PATH, PathGuard.resolveInside(root, "tautan").errorOrNull()?.kind)
    }

    @Test fun karakterNulDitolak() {
        val root = tmp.newFolder("root")
        assertNotNull(PathGuard.resolveInside(root, "a\u0000b").errorOrNull())
    }
}
