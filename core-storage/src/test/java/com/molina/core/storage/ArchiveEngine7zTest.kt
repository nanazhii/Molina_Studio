// molina-27q
package com.molina.core.storage

import com.molina.core.common.SevenZipCommand
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class ArchiveEngine7zTest {
    @get:Rule val tmp = TemporaryFolder()

    private val pw = "Rahasia-Kuat-9!"

    private class Fake(
        var reason: String? = null,
        var failWith: SevenZipCommand.Failure? = null,
    ) : SevenZipWriter {
        var job: SevenZipJob? = null
        override fun unavailableReason(): String? = reason
        override fun create(job: SevenZipJob, cancelled: () -> Boolean, onPercent: (Int) -> Unit) {
            this.job = job
            failWith?.let { throw SevenZipFailure(it) }
            onPercent(50)
            job.output.writeText("fake-7z")
            onPercent(100)
        }
    }

    @After fun reset() {
        SevenZipBackend.writer = null
    }

    private fun dir(name: String): File = File(tmp.root, name).also { it.mkdirs() }

    private fun sample(): File {
        val src = dir("src")
        File(src, "a.txt").writeText("alpha")
        File(src, "sub").mkdirs()
        File(src, "sub/b.txt").writeText("bravo")
        return src
    }

    private fun run7z(src: List<File>, out: File, pass: String? = pw, enc: Boolean = false, cancel: () -> Boolean = { false }) =
        ArchiveEngine.compress(
            src, out, CompressOptions(ArchiveFormat.SEVEN_Z, 6, "paket", pass, enc),
            ArchiveCtl(ArchiveKind.COMPRESS, cancel) {},
        )

    private fun assertReason(reason: ArchiveFailure, block: () -> Unit) {
        try {
            block()
            fail("Seharusnya gagal dengan $reason")
        } catch (e: ArchiveException) {
            assertEquals(reason, e.reason)
        }
    }

    private fun leftovers(out: File) = out.listFiles()!!.map { it.name }

    @Test fun sandiDiteruskanKeBackendDanArsipDipindahKeNamaAkhir() {
        SevenZipBackend.writer = Fake()
        val src = sample()
        val out = dir("out")
        val r = run7z(listOf(src), out, enc = true)
        assertEquals(listOf("paket.7z"), leftovers(out)) // staging sudah bersih
        assertEquals("paket.7z", r.file!!.name)
        assertEquals(4, r.entries) // src, a.txt, sub, sub/b.txt
    }

    @Test fun jobMembawaSandiNamaTerenkripsiDanSumber() {
        val fake = Fake().also { SevenZipBackend.writer = it }
        val src = sample()
        run7z(listOf(src), dir("out"), enc = true)
        val j = fake.job!!
        assertEquals(pw, j.password)
        assertTrue(j.encryptNames)
        assertEquals(listOf(src), j.sources)
        assertEquals(6, j.level)
        assertEquals(SevenZipCommand.OUT_FILE, j.output.name)
        assertEquals(j.stageDir, j.output.parentFile)
    }

    @Test fun tautanSimbolikDilewatiDanDikecualikan() {
        val fake = Fake().also { SevenZipBackend.writer = it }
        val src = sample()
        Files.createSymbolicLink(File(src, "tautan").toPath(), File(tmp.root, "luar").also { it.mkdirs() }.toPath())
        val outer = File(tmp.root, "outer.txt").also { it.writeText("x") }
        val topLink = File(tmp.root, "topLink")
        Files.createSymbolicLink(topLink.toPath(), outer.toPath())
        val r = run7z(listOf(src, topLink), dir("out"))
        assertEquals(listOf("src/tautan"), fake.job!!.excludes)
        assertEquals(listOf(src), fake.job!!.sources) // tautan tingkat atas disaring
        assertEquals(2, r.skipped)
    }

    @Test fun sandiLemahDitolakTanpaMenyentuhBackend() {
        val fake = Fake().also { SevenZipBackend.writer = it }
        val out = dir("out")
        assertReason(ArchiveFailure.WEAK_PASSWORD) { run7z(listOf(sample()), out, pass = "x") }
        assertEquals(null, fake.job)
        assertTrue(leftovers(out).isEmpty())
    }

    @Test fun tanpaBackendDitolakBukanDitulisTanpaSandi() {
        SevenZipBackend.writer = null
        val out = dir("out")
        assertReason(ArchiveFailure.TOOL_MISSING) { run7z(listOf(sample()), out) }
        assertTrue("tidak boleh ada keluaran", leftovers(out).isEmpty())
    }

    @Test fun backendTidakSiapDitolak() {
        SevenZipBackend.writer = Fake(reason = "7zip belum terpasang")
        val out = dir("out")
        assertReason(ArchiveFailure.TOOL_MISSING) { run7z(listOf(sample()), out) }
        assertTrue(leftovers(out).isEmpty())
    }

    @Test fun galatProsesDipetakanDanStagingDibersihkan() {
        val out = dir("out")
        val cases = mapOf(
            SevenZipCommand.Failure.DISK_FULL to ArchiveFailure.NO_SPACE,
            SevenZipCommand.Failure.OUT_OF_MEMORY to ArchiveFailure.OUT_OF_MEMORY,
            SevenZipCommand.Failure.SOURCE_UNREADABLE to ArchiveFailure.IO,
        )
        for ((f, reason) in cases) {
            SevenZipBackend.writer = Fake(failWith = f)
            assertReason(reason) { run7z(listOf(sample()), out) }
            assertTrue("tidak boleh ada sisa: ${leftovers(out)}", leftovers(out).isEmpty())
        }
    }

    @Test fun dibatalkanMengembalikanHasilBatalTanpaSisa() {
        SevenZipBackend.writer = Fake(failWith = SevenZipCommand.Failure.CANCELLED)
        val out = dir("out")
        val r = run7z(listOf(sample()), out)
        assertTrue(r.cancelled)
        assertTrue(leftovers(out).isEmpty())
    }

    @Test fun tujuanDiDalamFolderSumberDitolak() {
        val fake = Fake().also { SevenZipBackend.writer = it }
        val src = sample()
        val inside = File(src, "keluar").also { it.mkdirs() }
        assertReason(ArchiveFailure.IO) { run7z(listOf(src), inside) }
        assertEquals(null, fake.job)
    }

    @Test fun namaBentrokDiberiSufiks() {
        SevenZipBackend.writer = Fake()
        val out = dir("out")
        File(out, "paket.7z").writeText("lama")
        val r = run7z(listOf(sample()), out)
        assertEquals("paket (1).7z", r.file!!.name)
        assertEquals("lama", File(out, "paket.7z").readText())
    }

    @Test fun formatLainTidakTerpengaruh() {
        SevenZipBackend.writer = null
        val out = dir("out")
        val r = ArchiveEngine.compress(
            listOf(sample()), out, CompressOptions(ArchiveFormat.SEVEN_Z, 6, "polos", null),
            ArchiveCtl(ArchiveKind.COMPRESS, { false }) {},
        )
        assertNotNull(r.file)
        assertFalse(r.cancelled)
        assertEquals("polos.7z", r.file!!.name) // 7z tanpa sandi tetap lewat commons-compress
    }
}
