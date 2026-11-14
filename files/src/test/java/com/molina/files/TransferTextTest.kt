// molina-patch26c
package com.molina.files

import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaError
import com.molina.core.storage.BatchResult
import com.molina.core.storage.TransferKind
import com.molina.core.storage.TransferProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class TransferTextTest {
    private fun result(
        kind: TransferKind = TransferKind.COPY,
        ok: Int = 0,
        failed: Int = 0,
        skipped: Int = 0,
        cancelled: Boolean = false,
        error: MolinaError? = null,
    ) = BatchResult(kind, ok, failed, skipped, cancelled, error)

    @Test fun barisProgres() {
        val p = TransferProgress(TransferKind.COPY, 1, 5, 450, 1000)
        assertEquals("Menyalin 2/5 \u00B7 45% \u00B7 1000 B", TransferText.progressLine(TransferKind.COPY, p, false))
        assertEquals("Memindahkan\u2026", TransferText.progressLine(TransferKind.MOVE, null, false))
        assertEquals("Membatalkan\u2026", TransferText.progressLine(TransferKind.COPY, p, true))
    }

    @Test fun nomorItemTidakMelebihiJumlah() {
        val p = TransferProgress(TransferKind.DELETE, 3, 3, 0, 0)
        assertEquals("Menghapus 3/3 \u00B7 100%", TransferText.progressLine(TransferKind.DELETE, p, false))
    }

    @Test fun labelKlip() {
        assertEquals("Salin 3 item", TransferText.clipLabel(TransferKind.COPY, 3))
        assertEquals("Pindah 2 item", TransferText.clipLabel(TransferKind.MOVE, 2))
    }

    @Test fun ringkasanBerhasil() {
        assertEquals("3 item disalin", TransferText.summary(result(ok = 3)))
        assertEquals("2 item dipindahkan, 1 dilewati", TransferText.summary(result(TransferKind.MOVE, ok = 2, skipped = 1)))
        assertEquals("4 item dihapus", TransferText.summary(result(TransferKind.DELETE, ok = 4)))
    }

    @Test fun ringkasanSemuaDilewati() {
        assertEquals(
            "Tidak ada yang berubah (2 item sudah di folder ini)",
            TransferText.summary(result(TransferKind.MOVE, skipped = 2)),
        )
    }

    @Test fun ringkasanGagalMenyebutAlasan() {
        val e = MolinaError(ErrorKind.PROTECTED, "x")
        assertEquals(
            "1 item dihapus, 1 gagal: Folder standar Molina dilindungi",
            TransferText.summary(result(TransferKind.DELETE, ok = 1, failed = 1, error = e)),
        )
    }

    @Test fun ringkasanDibatalkan() {
        assertEquals("Dibatalkan. 1 item disalin", TransferText.summary(result(ok = 1, cancelled = true)))
        assertEquals("Dibatalkan. 0 item disalin, 1 gagal", TransferText.summary(result(failed = 1, cancelled = true)))
    }
}
