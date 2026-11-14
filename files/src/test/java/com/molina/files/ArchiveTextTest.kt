// molina-patch26k2
package com.molina.files

import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaError
import com.molina.core.storage.ArchiveException
import com.molina.core.storage.ArchiveFailure
import com.molina.core.storage.ArchiveKind
import com.molina.core.storage.ArchiveProgress
import com.molina.core.storage.CompressResult
import com.molina.core.storage.ExtractResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveTextTest {
    @Test fun ringkasanEkstrakMemuatUkuranDanDilewati() {
        assertEquals("12 berkas diekstrak \u00B7 3.4 MB", ArchiveText.extractSummary(ExtractResult("d/p", 12, 0, 0, false, 3_565_158L), false))
        assertEquals(
            "5 berkas diekstrak \u00B7 1.0 KB, 2 diberi nama baru, 1 dilewati",
            ArchiveText.extractSummary(ExtractResult("d", 5, 2, 1, false, 1024L), false),
        )
    }

    @Test fun ringkasanSubtitleDanBatal() {
        assertEquals("3 subtitle diekstrak \u00B7 45.0 KB", ArchiveText.extractSummary(ExtractResult("d", 3, 0, 0, false, 46_080L), true))
        assertEquals("Dibatalkan. Tidak ada yang diekstrak", ArchiveText.extractSummary(ExtractResult("", 0, 0, 0, true, 0L), false))
        assertEquals("1 berkas diekstrak", ArchiveText.extractSummary(ExtractResult("d", 1, 0, 0, false, 0L), false))
    }

    @Test fun ringkasanKompres() {
        assertEquals("Arsip dibuat \u00B7 1.0 MB (5 item), 1 dilewati", ArchiveText.compressSummary(CompressResult("o.zip", 5, 1, false, 1_048_576L)))
        assertEquals("Arsip dibuat \u00B7 10 B (1 item)", ArchiveText.compressSummary(CompressResult("o.zip", 1, 0, false, 10L)))
        assertEquals("Dibatalkan. Arsip tidak dibuat", ArchiveText.compressSummary(CompressResult("", 0, 0, true, 0L)))
    }

    @Test fun barisProgres() {
        val p = ArchiveProgress(ArchiveKind.EXTRACT, 450, 1000)
        assertEquals("Mengekstrak 45% \u00B7 1000 B", ArchiveText.progressLine(ArchiveOp.EXTRACT, p, false))
        assertEquals("Mengompres\u2026", ArchiveText.progressLine(ArchiveOp.COMPRESS, null, false))
        assertEquals("Membatalkan\u2026", ArchiveText.progressLine(ArchiveOp.EXTRACT_SUBS, p, true))
        assertEquals("Mengekstrak subtitle 0%", ArchiveText.progressLine(ArchiveOp.EXTRACT_SUBS, ArchiveProgress(ArchiveKind.EXTRACT, 0, 0), false))
    }

    @Test fun semuaAlasanPunyaTeksTanpaPath() {
        for (r in ArchiveFailure.values()) {
            val t = ArchiveText.failure(r)
            assertFalse(r.name, t.isBlank())
            assertFalse(r.name, t.contains('/'))
        }
        assertEquals("Tidak ada subtitle di arsip ini", ArchiveText.failure(ArchiveFailure.NO_MATCH, subs = true))
    }

    @Test fun galatDibacaDariCause() {
        val e = MolinaError(ErrorKind.IO, "x", ArchiveException(ArchiveFailure.WRONG_PASSWORD, "Kata sandi salah"))
        assertEquals(ArchiveFailure.WRONG_PASSWORD, ArchiveText.reasonOf(e))
        assertEquals("Kata sandi salah", ArchiveText.error(e, ArchiveOp.EXTRACT))
        val plain = MolinaError(ErrorKind.PERMISSION_DENIED, "Akses ditolak")
        assertNull(ArchiveText.reasonOf(plain))
        assertEquals(FilesErrorText.forOp(ErrorKind.PERMISSION_DENIED), ArchiveText.error(plain, ArchiveOp.COMPRESS))
    }

    @Test fun masalahNama() {
        assertEquals("Nama terlalu panjang (maks. 255 byte)", ArchiveText.nameProblem(ArchiveFailure.NAME_TOO_LONG))
        assertEquals("Nama arsip tidak valid", ArchiveText.nameProblem(ArchiveFailure.INVALID_NAME))
    }

    @Test fun teksSelesaiMemilihSumber() {
        val req = ArchiveRequest(ArchiveOp.EXTRACT_SUBS, listOf("Film/Sub.zip"), "Film")
        val ok = ArchiveState.Finished(1, req, ExtractResult("Film", 2, 0, 0, false, 2048L), null, null)
        assertEquals("2 subtitle diekstrak \u00B7 2.0 KB", ArchiveText.finished(ok))
        val err = MolinaError(ErrorKind.IO, "x", ArchiveException(ArchiveFailure.NO_MATCH, "x"))
        assertEquals("Tidak ada subtitle di arsip ini", ArchiveText.finished(ArchiveState.Finished(2, req, null, null, err)))
        assertTrue(ArchiveText.finished(ArchiveState.Finished(3, req, null, null, null)).isNotBlank())
    }
}
