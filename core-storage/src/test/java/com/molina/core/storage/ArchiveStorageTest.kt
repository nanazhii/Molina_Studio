// molina-patch26k
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private object ArchiveTestDispatchers : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Unconfined
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
}

class ArchiveStorageTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun storage(root: File) = LocalMolinaStorage(MolinaPaths(root), ArchiveTestDispatchers)

    private fun sample(): File {
        val root = tmp.newFolder("Molina")
        File(root, "src/sub").mkdirs()
        File(root, "out").mkdirs()
        File(root, "dest").mkdirs()
        File(root, "src/a.txt").writeText("alpha")
        File(root, "src/sub/b.txt").writeText("bravo")
        return root
    }

    @Test fun kompresDaftarDanEkstrakLewatStorage() = runBlocking {
        val root = sample()
        val s = storage(root)
        val c = s.compressItems(listOf("src"), "out", CompressOptions(ArchiveFormat.ZIP, 6, "paket")).getOrNull()!!
        assertEquals("out/paket.zip", c.outputRelative)
        assertFalse(c.cancelled)
        val listing = s.listArchive("out/paket.zip").getOrNull()!!
        assertEquals(2, listing.fileCount)
        val r = s.extractArchive("out/paket.zip", "dest", ExtractTarget.NAMED_FOLDER).getOrNull()!!
        assertEquals("dest/paket", r.destRelative)
        assertEquals("alpha", File(root, "dest/paket/src/a.txt").readText())
    }

    @Test fun galatMembawaAlasanUntukUi() = runBlocking {
        val root = sample()
        val s = storage(root)
        s.compressItems(listOf("src"), "out", CompressOptions(ArchiveFormat.ZIP, 6, "rahasia", "kunci")).getOrNull()!!
        val err = s.extractArchive("out/rahasia.zip", "dest", ExtractTarget.HERE).errorOrNull()
        assertNotNull(err)
        assertEquals(ArchiveFailure.NEED_PASSWORD, (err!!.cause as? ArchiveException)?.reason)
        assertTrue(File(root, "dest").listFiles()!!.isEmpty())
    }

    @Test fun pathKeluarRootDitolak() = runBlocking {
        val root = sample()
        val s = storage(root)
        assertEquals(ErrorKind.INVALID_PATH, s.listArchive("../x.zip").errorOrNull()?.kind)
        assertEquals(ErrorKind.INVALID_PATH, s.extractArchive("out/x.zip", "../luar", ExtractTarget.HERE).errorOrNull()?.kind)
    }

    @Test fun isiSampahTidakIkutDikompres() = runBlocking {
        val root = sample()
        val s = storage(root)
        s.trash(listOf("src/a.txt")).getOrNull()!!
        val c = s.compressItems(listOf("src", MolinaPaths.TRASH_DIR), "out", CompressOptions(ArchiveFormat.TAR, 0, "p")).getOrNull()!!
        assertEquals(1, c.skipped)
        val listing = s.listArchive("out/p.tar").getOrNull()!!
        assertEquals(1, listing.fileCount) // hanya sub/b.txt
    }
}
