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
}
