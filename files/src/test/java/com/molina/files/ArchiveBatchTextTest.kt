// molina-patch26k4
package com.molina.files

import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaError
import com.molina.core.storage.ArchiveBatchItem
import com.molina.core.storage.ArchiveBatchResult
import com.molina.core.storage.ArchiveException
import com.molina.core.storage.ArchiveFailure
import com.molina.core.storage.ArchiveKind
import com.molina.core.storage.ArchiveProgress
import com.molina.core.storage.ArchiveTestResult
import com.molina.core.storage.ExtractResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveBatchTextTest {
    private fun ok(name: String, bytes: Long = 0L, renamed: Int = 0, skipped: Int = 0, replaced: Int = 0) =
        ArchiveBatchItem(name, ExtractResult("out", 1, renamed, skipped, false, bytes, replaced), null)

    private fun fail(name: String, reason: ArchiveFailure): ArchiveBatchItem {
        val ex = ArchiveException(reason, "x")
        return ArchiveBatchItem(name, null, MolinaError(ErrorKind.IO, "x", ex))
    }

    @Test fun ringkasanCampuran() {
        val b = ArchiveBatchResult(
            listOf(ok("a.zip", 1024L), fail("b.zip", ArchiveFailure.CORRUPT), fail("c.zip", ArchiveFailure.NEED_PASSWORD)),
            3, false,
        )
        assertEquals(1, b.succeeded)
        assertEquals(1, b.failed)
        assertEquals(listOf("c.zip"), b.needPassword)
        assertEquals(
            "1 arsip diekstrak \u00B7 1.0 KB, 1 gagal (Arsip rusak atau tidak lengkap), 1 butuh kata sandi",
            ArchiveText.batchSummary(b),
        )
    }

    @Test fun ringkasanSemuaBerhasilMenghilangkanBagianNol() {
        val b = ArchiveBatchResult(listOf(ok("a.zip"), ok("b.zip")), 2, false)
        assertEquals("2 arsip diekstrak", ArchiveText.batchSummary(b))
    }

    @Test fun ringkasanMemuatKebijakan() {
        val b = ArchiveBatchResult(listOf(ok("a.zip", renamed = 2, skipped = 1, replaced = 3)), 1, false)
        assertEquals("1 arsip diekstrak, 2 diberi nama baru, 1 dilewati, 3 diganti", ArchiveText.batchSummary(b))
    }

    @Test fun ringkasanDibatalkan() {
        val b = ArchiveBatchResult(listOf(ok("a.zip", 1024L)), 3, true)
        assertEquals(2, b.notRun)
        assertEquals("Dibatalkan. 1 dari 3 arsip sudah diekstrak \u00B7 1.0 KB", ArchiveText.batchSummary(b))
    }

    @Test fun ringkasanUjiArsip() {
        assertEquals("Arsip utuh \u00B7 4 entri \u00B7 10 B", ArchiveText.testSummary(ArchiveTestResult(4, 10L, null, -1, true)))
        assertTrue(ArchiveText.testSummary(ArchiveTestResult(1, 10L, null, -1, false)).contains("TAR polos"))
        assertEquals(
            "Arsip rusak atau tidak lengkap\nEntri pertama yang rusak: nomor 3 (setelah 2 entri utuh)",
            ArchiveText.testSummary(ArchiveTestResult(2, 5L, ArchiveFailure.CORRUPT, 2, true)),
        )
        assertEquals(
            "Arsip rusak atau tidak lengkap\nStruktur arsip tidak terbaca",
            ArchiveText.testSummary(ArchiveTestResult(0, 0L, ArchiveFailure.CORRUPT, -1, true)),
        )
        assertEquals("Pengujian dibatalkan", ArchiveText.testSummary(ArchiveTestResult(0, 0L, null, -1, true, cancelled = true)))
    }

    @Test fun barisProgresBatchDanKlip() {
        val p = ArchiveProgress(ArchiveKind.EXTRACT, 450, 1000)
        assertEquals("Arsip 2/3 \u00B7 Mengekstrak 45% \u00B7 1000 B", ArchiveText.runningLine(ArchiveState.Running(ArchiveOp.EXTRACT, p, false, 1, 3)))
        assertEquals("Mengekstrak 45% \u00B7 1000 B", ArchiveText.runningLine(ArchiveState.Running(ArchiveOp.EXTRACT, p, false)))
        assertEquals("Membatalkan\u2026", ArchiveText.runningLine(ArchiveState.Running(ArchiveOp.EXTRACT, p, true, 1, 3)))
        assertEquals("Menguji arsip\u2026", ArchiveText.runningLine(ArchiveState.Running(ArchiveOp.TEST, null, false)))
        assertEquals("Ekstrak arsip ke\u2026", ArchiveText.clipLabel(1))
        assertEquals("Ekstrak 3 arsip ke\u2026", ArchiveText.clipLabel(3))
    }

    @Test fun peringatanTimpaMenyebutBerkasDiganti() {
        val w = ArchiveText.overwriteWarning()
        assertTrue(w.contains("diganti"))
        assertTrue(w.contains("dikembalikan"))
    }

    @Test fun ringkasanFinishedMemilihBatchDanTest() {
        val req = ArchiveRequest(ArchiveOp.EXTRACT, listOf("a.zip", "b.zip"), "")
        val batch = ArchiveBatchResult(listOf(ok("a.zip"), ok("b.zip")), 2, false)
        assertEquals("2 arsip diekstrak", ArchiveText.finished(ArchiveState.Finished(1, req, null, null, null, batch, null)))
        val test = ArchiveTestResult(1, 1L, null, -1, true)
        assertEquals(ArchiveText.testSummary(test), ArchiveText.finished(ArchiveState.Finished(2, req, null, null, null, null, test)))
    }
}
