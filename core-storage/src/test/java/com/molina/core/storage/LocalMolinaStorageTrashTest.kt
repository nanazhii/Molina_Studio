// molina-patch26h
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private object TrashTestDispatchers : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Unconfined
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
}

class LocalMolinaStorageTrashTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun storage(root: File) = LocalMolinaStorage(MolinaPaths(root), TrashTestDispatchers)

    @Test fun trashLaluRestoreMengembalikanBerkas() = runBlocking {
        val root = tmp.newFolder("Molina")
        File(root, "a.txt").writeText("isi")
        val s = storage(root)
        val items = s.trash(listOf("a.txt")).getOrNull()!!
        assertFalse(File(root, "a.txt").exists())
        assertEquals(1, s.listTrash().getOrNull()!!.size)
        assertEquals(listOf("a.txt"), s.restoreTrash(items.map { it.id }).getOrNull())
        assertEquals("isi", File(root, "a.txt").readText())
    }

    @Test fun folderSampahTidakTampilDiDaftarMeskiTersembunyiDitampilkan() = runBlocking {
        val root = tmp.newFolder("Molina")
        File(root, "a.txt").writeText("x")
        val s = storage(root)
        s.trash(listOf("a.txt"))
        assertTrue(File(root, MolinaPaths.TRASH_DIR).isDirectory)
        assertFalse(s.list().getOrNull()!!.any { it.name == MolinaPaths.TRASH_DIR })
        val shown = s.browse("", ListOptions(showHidden = true)).getOrNull()!!.entries
        assertFalse(shown.any { it.name == MolinaPaths.TRASH_DIR })
    }

    @Test fun folderSampahDilindungiDariHapusGantiNamaDanPindah() = runBlocking {
        val root = tmp.newFolder("Molina")
        File(root, "a.txt").writeText("x")
        val s = storage(root)
        s.trash(listOf("a.txt"))
        val t = MolinaPaths.TRASH_DIR
        assertEquals(ErrorKind.PROTECTED, s.delete(t).errorOrNull()?.kind)
        assertEquals(ErrorKind.PROTECTED, s.rename(t, "x").errorOrNull()?.kind)
        assertEquals(ErrorKind.PROTECTED, s.deleteAll(listOf(t)).getOrNull()?.firstError?.kind ?: ErrorKind.PROTECTED)
        assertEquals(ErrorKind.PROTECTED, s.trash(listOf(t)).errorOrNull()?.kind)
    }

    @Test fun folderStandarTidakBisaDimasukkanKeSampah() = runBlocking {
        val root = tmp.newFolder("Molina")
        val s = storage(root)
        s.ensureLayout()
        assertEquals(ErrorKind.PROTECTED, s.trash(listOf("cinema")).errorOrNull()?.kind)
        assertTrue(File(root, "cinema").isDirectory)
    }

    @Test fun pathKeluarRootDitolak() = runBlocking {
        val root = tmp.newFolder("Molina")
        assertEquals(ErrorKind.INVALID_PATH, storage(root).trash(listOf("../luar")).errorOrNull()?.kind)
    }
}
