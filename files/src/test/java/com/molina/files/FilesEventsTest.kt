// molina-patch13c
package com.molina.files

import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaError
import com.molina.log.LogEntry
import com.molina.log.LogLevel
import com.molina.log.LogSink
import com.molina.log.MolinaLogger
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FilesEventsTest {
    private class CaptureSink : LogSink {
        val entries = CopyOnWriteArrayList<LogEntry>()
        override fun write(entry: LogEntry) { entries += entry }
    }

    private val sink = CaptureSink()
    private val logger = MolinaLogger(listOf(sink))

    @Test
    fun depthCountsPathSegments() {
        assertEquals(0, FilesEvents.depth(""))
        assertEquals(1, FilesEvents.depth("Movies"))
        assertEquals(2, FilesEvents.depth("Movies/2024"))
    }

    @Test
    fun listedHasDepthAndCountOnly() {
        FilesEvents.listed("Movies/2024", 5, logger)
        val e = sink.entries.single()
        assertEquals("Molina/files", e.tag)
        assertEquals(LogLevel.INFO, e.level)
        assertEquals("[BEHAV:files.list] | depth=2 count=5", e.message)
    }

    @Test
    fun permissionEventsAreBehaviorNotBug() {
        FilesEvents.permissionMissing("", logger)
        FilesEvents.permissionRequested(logger)
        FilesEvents.permissionResult(false, logger)
        assertEquals(
            listOf(
                "[BEHAV:files.permission-missing] | depth=0",
                "[BEHAV:files.permission-request]",
                "[BEHAV:files.permission-result] | granted=false",
            ),
            sink.entries.map { it.message },
        )
    }

    @Test
    fun permissionDeniedIsBugWithErrnoAndNoPath() {
        val cause = IOException("/storage/emulated/0/Molina/rahasia: open failed: EACCES (Permission denied)")
        FilesEvents.failed("list", MolinaError(ErrorKind.PERMISSION_DENIED, "Akses ditolak", cause), "Movies", logger)
        val e = sink.entries.single()
        assertEquals(LogLevel.ERROR, e.level)
        assertEquals(
            "[BUG:files.list-failed] Akses ditolak | kind=PERMISSION_DENIED error=IOException depth=1 errno=EACCES",
            e.message,
        )
        assertFalse("rahasia" in e.message)
        assertNull(e.throwable)
    }

    @Test
    fun notFoundIsWarnLevel() {
        FilesEvents.failed("list", MolinaError(ErrorKind.NOT_FOUND, "Tidak ditemukan", FileNotFoundException()), "", logger)
        val e = sink.entries.single()
        assertEquals(LogLevel.WARN, e.level)
        assertEquals("[BUG:files.list-failed] Tidak ditemukan | kind=NOT_FOUND error=FileNotFoundException depth=0", e.message)
    }

    @Test
    fun layoutFailureHasOwnCodeAndNoCauseIsNone() {
        FilesEvents.failed("layout", MolinaError(ErrorKind.IO, "Kesalahan I/O"), "", logger)
        assertEquals(
            "[BUG:files.layout-failed] Kesalahan I/O | kind=IO error=none depth=0",
            sink.entries.single().message,
        )
    }

    @Test
    fun errnoIsFoundInCauseChainOnly() {
        val chain = RuntimeException("luar", IOException("ENOSPC (No space left on device)"))
        assertEquals("ENOSPC", FilesEvents.errnoOf(chain))
        assertNull(FilesEvents.errnoOf(IOException("folder bernama EMPTY")))
        assertNull(FilesEvents.errnoOf(null))
    }

    // molina-patch26b
    @Test
    fun opHanyaJenisDanKedalamanTanpaNama() {
        FilesEvents.op("rename", false, "Movies/2024/rahasia.mkv", logger)
        FilesEvents.op("create-folder", true, "", logger)
        assertEquals(
            listOf(
                "[BEHAV:files.op-rename] | kind=file depth=3",
                "[BEHAV:files.op-create-folder] | kind=folder depth=0",
            ),
            sink.entries.map { it.message },
        )
        assertFalse("rahasia" in sink.entries.first().message)
    }

    @Test
    fun pencarianTidakMencatatKueri() {
        FilesEvents.searched("Movies", 7, deep = true, truncated = false, logger = logger)
        assertEquals(
            "[BEHAV:files.search] | depth=1 deep=true count=7 truncated=false",
            sink.entries.single().message,
        )
    }

    @Test
    fun galatNamaSudahAdaDanDilindungiBerlevelWarn() {
        FilesEvents.failed("create-folder", MolinaError(ErrorKind.ALREADY_EXISTS, "Nama sudah dipakai"), "Movies", logger)
        FilesEvents.failed("delete", MolinaError(ErrorKind.PROTECTED, "Folder ini dilindungi"), "", logger)
        assertEquals(listOf(LogLevel.WARN, LogLevel.WARN), sink.entries.map { it.level })
        assertEquals(
            "[BUG:files.create-folder-failed] Nama sudah dipakai | kind=ALREADY_EXISTS error=none depth=1",
            sink.entries.first().message,
        )
    }
}
