// molina-patch26k5c
package com.molina.files

import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaError
import com.molina.core.storage.ArchiveException
import com.molina.core.storage.ArchiveFailure
import com.molina.log.LogEntry
import com.molina.log.LogLevel
import com.molina.log.LogSink
import com.molina.log.MolinaLogger
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilesArchiveViewEventsTest {
    private class CaptureSink : LogSink {
        val entries = CopyOnWriteArrayList<LogEntry>()
        override fun write(entry: LogEntry) { entries += entry }
    }

    private val sink = CaptureSink()
    private val logger = MolinaLogger(listOf(sink))

    @Test fun lihatIsiHanyaMemuatFormatJumlahDanStatus() {
        FilesEvents.archiveView("zip", 3, 1, false, true, 2, logger)
        val e = sink.entries.single()
        assertEquals("Molina/files", e.tag)
        assertEquals(LogLevel.INFO, e.level)
        assertEquals("[BEHAV:files.archive-view] | format=zip files=3 folders=1 encrypted=false truncated=true skipped=2", e.message)
    }

    @Test fun bukaEntriTanpaNamaDanPath() {
        FilesEvents.archiveOpen("7z", false, 1024L, false, logger)
        assertEquals("[BEHAV:files.archive-open] | format=7z cached=false bytes=1024 cancelled=false", sink.entries.single().message)
    }

    @Test fun cacheDibersihkanMemuatAlasanDanByte() {
        FilesEvents.archiveCache("start", 0L, logger)
        assertEquals("[BEHAV:files.archive-cache] | why=start freed=0", sink.entries.single().message)
    }

    @Test fun terlaluBesarAdalahWarnDenganOpOpen() {
        val ex = ArchiveException(ArchiveFailure.TOO_LARGE, "Berkas terlalu besar")
        FilesEvents.archiveFailed("open", MolinaError(ErrorKind.IO, ex.message.orEmpty(), ex), "Film", logger)
        val e = sink.entries.single()
        assertEquals(LogLevel.WARN, e.level)
        assertEquals(
            "[BUG:files.archive-failed] Berkas terlalu besar | op=open kind=IO reason=TOO_LARGE error=ArchiveException depth=1",
            e.message,
        )
    }

    @Test fun permintaanEkstrakBawaanTanpaPilihan() {
        assertNull(ArchiveRequest(ArchiveOp.EXTRACT, listOf("a.zip"), "").only)
    }

    @Test fun permintaanEkstrakMembawaKunciPilihan() {
        val r = ArchiveRequest(ArchiveOp.EXTRACT, listOf("a.zip"), "", only = setOf("x/", "y.txt"))
        assertEquals(setOf("x/", "y.txt"), r.only)
    }
}
