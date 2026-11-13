// molina-patch13c
package com.molina.terminal.proot

import com.molina.log.LogEntry
import com.molina.log.LogLevel
import com.molina.log.LogSink
import com.molina.log.MolinaLogger
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotEventsTest {
    private class CaptureSink : LogSink {
        val entries = CopyOnWriteArrayList<LogEntry>()
        override fun write(entry: LogEntry) { entries += entry }
    }

    private fun tmp(): File = Files.createTempDirectory("molina").toFile()

    private fun elfArm64Static(): ByteArray {
        val b = ByteArray(4096)
        b[0] = 0x7f; b[1] = 'E'.code.toByte(); b[2] = 'L'.code.toByte(); b[3] = 'F'.code.toByte()
        b[4] = 2; b[5] = 1
        b[18] = 183.toByte()
        b[32] = 64; b[54] = 56; b[56] = 1
        b[64] = 1
        return b
    }

    private fun goodRootfs(d: File): File {
        val rootfs = File(d, "rootfs")
        File(rootfs, "usr/bin").mkdirs()
        File(rootfs, "usr/bin/bash").writeText("x")
        File(rootfs, "usr/bin/env").writeText("x")
        return rootfs
    }

    @Test
    fun prepareGuestLogsRootfsThenCreatedFilesAndIsQuietOnRerun() {
        val d = tmp()
        try {
            val rootfs = goodRootfs(d)
            val sink = CaptureSink()
            val logger = MolinaLogger(listOf(sink))
            ProotLauncher.prepareGuest(rootfs, id = "t7", logger = logger)
            assertEquals(
                listOf(
                    "[BEHAV:proot.rootfs-checked] | readable=true bash=true env=true",
                    "[BEHAV:proot.guest-files] | created=resolv.conf+hosts+apt-conf",
                ),
                sink.entries.map { it.message },
            )
            assertTrue(sink.entries.all { it.tag == "Molina/proot/t7" })
            sink.entries.clear()
            ProotLauncher.prepareGuest(rootfs, id = "t7", logger = logger)
            assertEquals("[BEHAV:proot.guest-files] | created=none", sink.entries.last().message)
        } finally {
            d.deleteRecursively()
        }
    }

    @Test
    fun missingBashIsBug() {
        val d = tmp()
        try {
            val rootfs = File(d, "rootfs").also { it.mkdirs() }
            val sink = CaptureSink()
            ProotEvents.rootfs(MolinaLogger(listOf(sink)), "t1", rootfs)
            val e = sink.entries.single()
            assertEquals(LogLevel.ERROR, e.level)
            assertEquals("[BUG:proot.rootfs-no-shell] bash tidak ditemukan di rootfs | readable=true bash=false env=false", e.message)
        } finally {
            d.deleteRecursively()
        }
    }

    @Test
    fun unreadableRootfsIsBug() {
        val d = tmp()
        try {
            val sink = CaptureSink()
            ProotEvents.rootfs(MolinaLogger(listOf(sink)), null, File(d, "tidak-ada"))
            val e = sink.entries.single()
            assertEquals("Molina/proot", e.tag)
            assertEquals("[BUG:proot.rootfs-unreadable] rootfs tidak terbaca | readable=false bash=false env=false", e.message)
        } finally {
            d.deleteRecursively()
        }
    }

    @Test
    fun missingBinaryIsBug() {
        val d = tmp()
        try {
            val sink = CaptureSink()
            ProotLauncher.build(DebianLayout(d), d.path, id = "t2", logger = MolinaLogger(listOf(sink)))
            val e = sink.entries.single()
            assertEquals(LogLevel.ERROR, e.level)
            assertEquals("[BUG:proot.binary-missing] binary proot tidak ditemukan", e.message)
            assertEquals("Molina/proot/t2", e.tag)
        } finally {
            d.deleteRecursively()
        }
    }

    @Test
    fun launchBuiltReportsOptionsWithoutPaths() {
        val d = tmp()
        try {
            val layout = DebianLayout(d)
            layout.prootDir.mkdirs()
            layout.prootBin.writeBytes(elfArm64Static())
            layout.prootBin.setExecutable(true)
            File(layout.prootDir, "loader").writeText("x")
            val sink = CaptureSink()
            ProotLauncher.build(layout, d.path, noSeccomp = true, id = "t3", logger = MolinaLogger(listOf(sink)))
            val e = sink.entries.single()
            assertEquals(LogLevel.INFO, e.level)
            assertEquals("[BEHAV:proot.launch-built] | arm64=true loader=true libs=false seccomp=off", e.message)
        } finally {
            d.deleteRecursively()
        }
    }
}
