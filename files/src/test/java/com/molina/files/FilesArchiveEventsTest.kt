// molina-patch26k2
package com.molina.files

import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaError
import com.molina.core.storage.ArchiveException
import com.molina.core.storage.ArchiveFailure
import com.molina.log.LogEntry
import com.molina.log.LogLevel
import com.molina.log.LogSink
import com.molina.log.MolinaLogger
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilesArchiveEventsTest {
    private class CaptureSink : LogSink {
        val entries = CopyOnWriteArrayList<LogEntry>()
        override fun write(entry: LogEntry) { entries += entry }
    }

    private val sink = CaptureSink()
    private val logger = MolinaLogger(listOf(sink))

    @Test fun mulaiHanyaMemuatJenisFormatJumlahDanKedalaman() {
        FilesEvents.archiveStart("extract", 1, "zip", true, "Film/Subs", logger)
        val e = sink.entries.single()
        assertEquals("Molina/files", e.tag)
        assertEquals(LogLevel.INFO, e.level)
        assertEquals("[BEHAV:files.archive-start] | op=extract format=zip count=1 encrypted=true depth=2 policy=rename", e.message) // 26k4:e70
    }

    @Test fun selesaiMemuatJumlahUkuranDanTanpaPath() {
        FilesEvents.archiveDone("compress", 5, 0, 1, false, 1_048_576L, "", logger)
        assertEquals(
            "[BEHAV:files.archive-done] | op=compress ok=5 renamed=0 skipped=1 cancelled=false bytes=1048576 depth=0 replaced=0", // 26k4:e71
            sink.entries.single().message,
        )
    }

    @Test fun kataSandiDibutuhkanAdalahWarnTanpaErrno() {
        val ex = ArchiveException(ArchiveFailure.NEED_PASSWORD, "Arsip butuh kata sandi")
        FilesEvents.archiveFailed("extract", MolinaError(ErrorKind.IO, ex.message.orEmpty(), ex), "Film", logger)
        val e = sink.entries.single()
        assertEquals(LogLevel.WARN, e.level)
        assertEquals(
            "[BUG:files.archive-failed] Arsip butuh kata sandi | op=extract kind=IO reason=NEED_PASSWORD error=ArchiveException depth=1",
            e.message,
        )
    }

    @Test fun diskPenuhAdalahErrorDenganErrno() {
        val ex = ArchiveException(ArchiveFailure.NO_SPACE, "Ruang penyimpanan tidak cukup", IOException("write failed: ENOSPC (No space left on device)"))
        FilesEvents.archiveFailed("compress", MolinaError(ErrorKind.NO_SPACE, ex.message.orEmpty(), ex), "", logger)
        val e = sink.entries.single()
        assertEquals(LogLevel.ERROR, e.level)
        assertTrue(e.message, e.message.contains("reason=NO_SPACE"))
        assertTrue(e.message, e.message.endsWith("errno=ENOSPC"))
    }

    @Test fun galatTanpaAlasanTetapDicatat() {
        FilesEvents.archiveFailed("extract", MolinaError(ErrorKind.PERMISSION_DENIED, "Akses ditolak"), "A/B/C", logger)
        val e = sink.entries.single()
        assertEquals(LogLevel.ERROR, e.level)
        assertTrue(e.message, e.message.contains("reason=none"))
        assertFalse(e.message.contains("A/B"))
    }
}
