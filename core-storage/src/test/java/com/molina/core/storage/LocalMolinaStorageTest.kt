package com.molina.core.storage

import com.molina.core.common.DispatcherProvider
import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaPaths
import com.molina.core.common.errorOrNull
import com.molina.core.common.getOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private object TestDispatchers : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Unconfined
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
}

class LocalMolinaStorageTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun storage(root: File) = LocalMolinaStorage(MolinaPaths(root), TestDispatchers)

    @Test fun ensureLayoutMembuatFolderDanIdempotent() = runBlocking {
        val root = File(tmp.root, "Molina")
        val s = storage(root)
        assertTrue(s.ensureLayout().getOrNull() != null)
        assertTrue(File(root, "logs").isDirectory && File(root, "terminal").isDirectory && File(root, "cinema").isDirectory)
        assertTrue(s.ensureLayout().getOrNull() != null)
    }

    @Test fun ensureLayoutGagalBilaRootAdalahBerkas() = runBlocking {
        val root = tmp.newFile("Molina")
        assertEquals(ErrorKind.IO, storage(root).ensureLayout().errorOrNull()?.kind)
    }

    @Test fun listUrutFolderDuluLaluNamaTanpaPeduliHurufBesar() = runBlocking {
        val root = tmp.newFolder("Molina")
        File(root, "b.txt").writeText("12345")
        File(root, "A.txt").writeText("1")
        File(root, "zdir").mkdir()
        File(root, "Adir").mkdir()
        val names = storage(root).list().getOrNull()!!.map { it.name }
        assertEquals(listOf("Adir", "zdir", "A.txt", "b.txt"), names)
    }

    @Test fun listMelaporkanUkuranBerkasDanNolUntukFolder() = runBlocking {
        val root = tmp.newFolder("Molina")
        File(root, "b.txt").writeText("12345")
        File(root, "d").mkdir()
        val byName = storage(root).list().getOrNull()!!.associateBy { it.name }
        assertEquals(5L, byName["b.txt"]!!.sizeBytes)
        assertEquals(0L, byName["d"]!!.sizeBytes)
        assertTrue(byName["d"]!!.isDirectory)
    }

    @Test fun listSubfolderRelatif() = runBlocking {
        val root = tmp.newFolder("Molina")
        File(root, "sub").mkdir()
        File(root, "sub/x.txt").writeText("x")
        assertEquals(listOf("x.txt"), storage(root).list("sub").getOrNull()!!.map { it.name })
    }

    @Test fun listFolderTidakAdaMenghasilkanNotFound() = runBlocking {
        val root = tmp.newFolder("Molina")
        assertEquals(ErrorKind.NOT_FOUND, storage(root).list("tidak-ada").errorOrNull()?.kind)
    }

    @Test fun listBerkasBukanFolderMenghasilkanIo() = runBlocking {
        val root = tmp.newFolder("Molina")
        File(root, "f.txt").writeText("x")
        assertEquals(ErrorKind.IO, storage(root).list("f.txt").errorOrNull()?.kind)
    }

    @Test fun listKeluarRootDitolak() = runBlocking {
        val root = tmp.newFolder("Molina")
        assertEquals(ErrorKind.INVALID_PATH, storage(root).list("..").errorOrNull()?.kind)
    }

    // molina-patch26a
    @Test fun browseKueriDanSortirLewatStorage() = runBlocking {
        val root = tmp.newFolder("Molina")
        File(root, "Film A.mkv").writeText("1")
        File(root, "Film B.mkv").writeText("12")
        File(root, "catatan.txt").writeText("x")
        val r = storage(root).browse("", ListOptions(query = "film", sortKey = SortKey.SIZE, ascending = false)).getOrNull()!!
        assertEquals(listOf("Film B.mkv", "Film A.mkv"), r.entries.map { it.name })
    }

    @Test fun browseKeluarRootDitolak() = runBlocking {
        val root = tmp.newFolder("Molina")
        assertEquals(ErrorKind.INVALID_PATH, storage(root).browse("..").errorOrNull()?.kind)
    }

    @Test fun createFolderDanFileDiSubfolder() = runBlocking {
        val root = tmp.newFolder("Molina")
        File(root, "sub").mkdir()
        val s = storage(root)
        assertTrue(s.createFolder("sub", "baru").getOrNull()!!.isDirectory)
        assertTrue(!s.createFile("sub", "a.sh").getOrNull()!!.isDirectory)
        assertTrue(File(root, "sub/baru").isDirectory && File(root, "sub/a.sh").isFile)
        assertEquals(ErrorKind.ALREADY_EXISTS, s.createFolder("sub", "baru").errorOrNull()?.kind)
        assertEquals(ErrorKind.INVALID_PATH, s.createFolder("../luar", "x").errorOrNull()?.kind)
    }

    @Test fun renameDanDeleteBerjalan() = runBlocking {
        val root = tmp.newFolder("Molina")
        File(root, "lama.txt").writeText("x")
        val s = storage(root)
        assertEquals("baru.txt", s.rename("lama.txt", "baru.txt").getOrNull()!!.name)
        assertTrue(s.delete("baru.txt").getOrNull() != null)
        assertTrue(!File(root, "baru.txt").exists())
    }

    @Test fun folderStandarDanRootDilindungi() = runBlocking {
        val root = tmp.newFolder("Molina")
        val s = storage(root)
        s.ensureLayout()
        assertEquals(ErrorKind.PROTECTED, s.delete("logs").errorOrNull()?.kind)
        assertEquals(ErrorKind.PROTECTED, s.rename("terminal", "x").errorOrNull()?.kind)
        assertEquals(ErrorKind.PROTECTED, s.delete("").errorOrNull()?.kind)
        assertTrue(File(root, "logs").isDirectory && File(root, "terminal").isDirectory)
    }

    // molina-patch26c
    @Test fun salinPindahHapusMassalLewatRelatif() = runBlocking {
        val root = File(tmp.root, "Molina")
        val s = storage(root)
        s.ensureLayout()
        File(root, "a.txt").writeText("A")
        File(root, "b.txt").writeText("B")
        File(root, "tujuan").mkdir()
        val copied = s.copy(listOf("a.txt", "b.txt"), "tujuan").getOrNull()!!
        assertEquals(2, copied.succeeded)
        assertTrue(File(root, "a.txt").exists() && File(root, "tujuan/a.txt").exists())
        val moved = s.move(listOf("a.txt"), "tujuan").getOrNull()!!
        assertEquals(1, moved.succeeded)
        assertTrue(!File(root, "a.txt").exists() && File(root, "tujuan/a (1).txt").exists())
        val deleted = s.deleteAll(listOf("b.txt", "tujuan")).getOrNull()!!
        assertEquals(2, deleted.succeeded)
        assertTrue(!File(root, "b.txt").exists() && !File(root, "tujuan").exists())
    }

    @Test fun pindahDanHapusMassalMenolakFolderStandar() = runBlocking {
        val root = File(tmp.root, "Molina")
        val s = storage(root)
        s.ensureLayout()
        File(root, "tujuan").mkdir()
        assertEquals(ErrorKind.PROTECTED, s.move(listOf("logs"), "tujuan").getOrNull()!!.firstError?.kind)
        assertEquals(ErrorKind.PROTECTED, s.deleteAll(listOf("logs", "terminal")).getOrNull()!!.firstError?.kind)
        assertTrue(File(root, "logs").isDirectory && File(root, "terminal").isDirectory)
    }

    @Test fun massalMenolakPathKeluarDariRoot() = runBlocking {
        val root = File(tmp.root, "Molina")
        val s = storage(root)
        s.ensureLayout()
        assertEquals(ErrorKind.INVALID_PATH, s.copy(listOf("../luar"), "").errorOrNull()?.kind)
        assertEquals(ErrorKind.INVALID_PATH, s.deleteAll(listOf("../luar")).errorOrNull()?.kind)
        assertEquals(ErrorKind.INVALID_PATH, s.copy(listOf("a"), "../luar").errorOrNull()?.kind)
    }

    @Test fun salinFolderRootKeSubfolderDitolak() = runBlocking {
        val root = File(tmp.root, "Molina")
        val s = storage(root)
        s.ensureLayout()
        val r = s.copy(listOf(""), "logs").getOrNull()!!
        assertEquals(ErrorKind.INVALID_PATH, r.firstError?.kind)
    }
}
