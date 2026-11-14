// molina-patch26k4
package com.molina.core.storage

import com.molina.core.common.DispatcherProvider
import com.molina.core.common.MolinaPaths
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

private object BatchTestDispatchers : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Unconfined
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
}

/** Ekstrak banyak arsip dan Uji arsip lewat LocalMolinaStorage (jalur nyata, arsip asli). */
class ArchiveBatchTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun storage(root: File) = LocalMolinaStorage(MolinaPaths(root), BatchTestDispatchers)

    private fun sample(): File {
        val root = tmp.newFolder("Molina")
        File(root, "in").mkdirs()
        File(root, "out").mkdirs()
        File(root, "src").mkdirs()
        File(root, "src/a.txt").writeText("alpha")
        return root
    }

    @Test fun campuranSuksesGagalDanBersandi() {
        runBlocking {
            val root = sample()
            val s = storage(root)
            s.compressItems(listOf("src"), "in", CompressOptions(ArchiveFormat.ZIP, 6, "good"))
            s.compressItems(listOf("src"), "in", CompressOptions(ArchiveFormat.ZIP, 6, "enc", "rahasia"))
            File(root, "in/bad.zip").writeText("ini bukan arsip zip")
            val seen = ArrayList<Pair<Int, Int>>()
            val r = s.extractArchives(
                listOf("in/good.zip", "in/bad.zip", "in/enc.zip"), "out", ExtractTarget.NAMED_FOLDER,
                ConflictPolicy.RENAME, CancelToken(), { i, n -> seen.add(i to n) }, {},
            ).getOrNull()!!
            assertEquals(3, r.total)
            assertEquals(1, r.succeeded)
            assertEquals(1, r.failed)
            assertEquals(listOf("in/enc.zip"), r.needPassword)
            assertFalse(r.cancelled)
            assertEquals(0, r.notRun)
            assertEquals(listOf(0 to 3, 1 to 3, 2 to 3), seen)
            assertEquals(ArchiveFailure.CORRUPT, r.items[1].reason)
            assertEquals(ArchiveFailure.NEED_PASSWORD, r.items[2].reason)
            assertEquals("alpha", File(root, "out/good/src/a.txt").readText())
            assertEquals("hanya hasil arsip yang sukses yang tersisa", listOf("good"), File(root, "out").list()!!.sorted())
        }
    }

    @Test fun arsipBersandiBisaDiekstrakSetelahSandiDiberikan() {
        runBlocking {
            val root = sample()
            val s = storage(root)
            s.compressItems(listOf("src"), "in", CompressOptions(ArchiveFormat.ZIP, 6, "enc", "rahasia"))
            val r = s.extractArchive("in/enc.zip", "out", ExtractTarget.HERE, "rahasia", null, CancelToken(), {}, false, ConflictPolicy.RENAME)
            assertEquals(1, r.getOrNull()!!.files)
            assertEquals("alpha", File(root, "out/src/a.txt").readText())
        }
    }

    @Test fun dibatalkanSebelumMulaiTidakMenjalankanApaPun() {
        runBlocking {
            val root = sample()
            val s = storage(root)
            s.compressItems(listOf("src"), "in", CompressOptions(ArchiveFormat.ZIP, 6, "good"))
            val token = CancelToken().also { it.cancel() }
            val r = s.extractArchives(listOf("in/good.zip"), "out", ExtractTarget.HERE, ConflictPolicy.RENAME, token).getOrNull()!!
            assertTrue(r.cancelled)
            assertEquals(1, r.notRun)
            assertEquals(0, r.succeeded)
            assertTrue(File(root, "out").list()!!.isEmpty())
        }
    }

    @Test fun kebijakanSkipDanOverwriteDiteruskanKeSetiapArsip() {
        runBlocking {
            val root = sample()
            val s = storage(root)
            s.compressItems(listOf("src"), "in", CompressOptions(ArchiveFormat.ZIP, 6, "satu"))
            File(root, "out/src").mkdirs()
            File(root, "out/src/a.txt").writeText("lama")
            val skip = s.extractArchives(listOf("in/satu.zip"), "out", ExtractTarget.HERE, ConflictPolicy.SKIP).getOrNull()!!
            assertEquals(1, skip.skipped)
            assertEquals("lama", File(root, "out/src/a.txt").readText())
            val over = s.extractArchives(listOf("in/satu.zip"), "out", ExtractTarget.HERE, ConflictPolicy.OVERWRITE).getOrNull()!!
            assertEquals(1, over.replaced)
            assertEquals("alpha", File(root, "out/src/a.txt").readText())
        }
    }

    @Test fun ujiArsipLewatStorage() {
        runBlocking {
            val root = sample()
            val s = storage(root)
            s.compressItems(listOf("src"), "in", CompressOptions(ArchiveFormat.ZIP, 6, "good"))
            File(root, "in/bad.zip").writeText("ini bukan arsip zip")
            val good = s.testArchive("in/good.zip").getOrNull()
            assertNotNull(good)
            assertTrue(good!!.ok)
            assertEquals(5L, good.bytes)
            val bad = s.testArchive("in/bad.zip").getOrNull()!!
            assertFalse(bad.ok)
            assertEquals(ArchiveFailure.CORRUPT, bad.failure)
            assertEquals(-1, bad.badIndex)
        }
    }
}
