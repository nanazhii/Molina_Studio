// molina-patch26j
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
import kotlin.coroutines.cancellation.CancellationException

private object PropsTestDispatchers : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Unconfined
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
}

class FilePropsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun storage(root: File) = LocalMolinaStorage(MolinaPaths(root), PropsTestDispatchers)

    private fun sample(): File {
        val root = tmp.newFolder("Molina")
        File(root, "sub").mkdirs()
        File(root, "a.txt").writeText("abc")
        File(root, "sub/b.txt").writeText("12345")
        return root
    }

    @Test fun folderDihitungRekursif() {
        val root = sample()
        val p = PropsReader.read(root, "", { false })
        assertTrue(p.isDirectory)
        assertEquals(8L, p.sizeBytes)
        assertEquals(2, p.fileCount)
        assertEquals(1, p.folderCount)
    }

    @Test fun berkasBiasaTanpaIsi() {
        val root = sample()
        val p = PropsReader.read(File(root, "a.txt"), "a.txt", { false })
        assertEquals(3L, p.sizeBytes)
        assertEquals(false, p.isDirectory)
        assertEquals(0, p.fileCount)
    }

    @Test fun sha256BerkasAbc() {
        val root = sample()
        val h = PropsReader.sha256(File(root, "a.txt"), { false }, { _, _ -> })
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", h)
    }

    @Test(expected = CancellationException::class) fun sha256DibatalkanMelemparPembatalan() {
        val root = sample()
        PropsReader.sha256(File(root, "a.txt"), { true }, { _, _ -> })
    }

    @Test fun propertiesMenjagaUrutanDanPathRelatif() = runBlocking {
        val s = storage(sample())
        val list = s.properties(listOf("sub", "a.txt")).getOrNull()!!
        assertEquals(listOf("sub", "a.txt"), list.map { it.path })
        assertEquals(listOf(true, false), list.map { it.isDirectory })
    }

    @Test fun propertiesDiLuarRootDitolak() = runBlocking {
        val s = storage(sample())
        assertEquals(ErrorKind.INVALID_PATH, s.properties(listOf("../luar")).errorOrNull()?.kind)
    }

    @Test fun propertiesItemHilangNotFound() = runBlocking {
        val s = storage(sample())
        assertEquals(ErrorKind.NOT_FOUND, s.properties(listOf("tidak-ada.txt")).errorOrNull()?.kind)
    }

    @Test fun sha256LewatStorageMelaporkanKemajuan() = runBlocking {
        val s = storage(sample())
        var last = -1L
        val h = s.sha256("a.txt") { done, _ -> last = done }.getOrNull()
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", h)
        assertEquals(3L, last)
    }

    @Test fun sha256FolderDitolak() = runBlocking {
        val s = storage(sample())
        assertTrue(s.sha256("sub").errorOrNull() != null)
    }

    @Test fun shareTargetsMelewatiFolderDanSampah() = runBlocking {
        val root = sample()
        File(root, ".molina-trash").mkdirs()
        File(root, ".molina-trash/x.txt").writeText("x")
        val t = storage(root).shareTargets(listOf("a.txt", "sub", ".molina-trash/x.txt")).getOrNull()!!
        assertEquals(1, t.files.size)
        assertEquals("a.txt", t.files[0].name)
        assertEquals(2, t.skipped)
    }
}
