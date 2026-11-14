// molina-patch26k4
package com.molina.files

import com.molina.log.LogEntry
import com.molina.log.LogLevel
import com.molina.log.LogSink
import com.molina.log.MolinaLogger
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Test

class FilesArchiveBatchEventsTest {
    private class CaptureSink : LogSink {
        val entries = CopyOnWriteArrayList<LogEntry>()
        override fun write(entry: LogEntry) { entries += entry }
    }

    private val sink = CaptureSink()
    private val logger = MolinaLogger(listOf(sink))

    @Test fun ringkasanBatchTanpaNamaDanPath() {
        FilesEvents.archiveBatch(3, 1, 1, 1, false, 4, 1, 0, 2, 2048L, "Film", logger)
        val e = sink.entries.single()
        assertEquals("Molina/files", e.tag)
        assertEquals(LogLevel.INFO, e.level)
        assertEquals(
            "[BEHAV:files.archive-batch] | total=3 ok=1 failed=1 needpw=1 cancelled=false files=4 renamed=1 skipped=0 replaced=2 bytes=2048 depth=1",
            e.message,
        )
    }

    @Test fun ujiArsipMemuatIndeksTanpaNama() {
        FilesEvents.archiveTested("zip", false, 3, 100L, "CORRUPT", 2, true, false, "", logger)
        assertEquals(
            "[BEHAV:files.archive-test] | format=zip ok=false entries=3 bytes=100 bad=2 reason=CORRUPT checksummed=true cancelled=false depth=0",
            sink.entries.single().message,
        )
    }

    @Test fun klipEkstrakKe() {
        FilesEvents.archiveClip("set", 2, logger)
        assertEquals("[BEHAV:files.archive-clip] | action=set count=2", sink.entries.single().message)
    }

    @Test fun mulaiMemuatKebijakan() {
        FilesEvents.archiveStart("extract", 3, "zip", false, "Film", logger, "overwrite")
        assertEquals(
            "[BEHAV:files.archive-start] | op=extract format=zip count=3 encrypted=false depth=1 policy=overwrite",
            sink.entries.single().message,
        )
    }

    @Test fun selesaiMemuatJumlahDiganti() {
        FilesEvents.archiveDone("extract", 5, 0, 1, false, 10L, "", logger, 2)
        assertEquals(
            "[BEHAV:files.archive-done] | op=extract ok=5 renamed=0 skipped=1 cancelled=false bytes=10 depth=0 replaced=2",
            sink.entries.single().message,
        )
    }
}
