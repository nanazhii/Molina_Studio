package com.molina.terminal.proot

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotLauncherTest {
    private fun tmp(): File = Files.createTempDirectory("molina").toFile()

    private fun elfArm64Static(): ByteArray {
        val b = ByteArray(4096)
        b[0] = 0x7f; b[1] = 'E'.code.toByte(); b[2] = 'L'.code.toByte(); b[3] = 'F'.code.toByte()
        b[4] = 2; b[5] = 1
        b[18] = 183.toByte() // EM_AARCH64 = 0x00B7
        b[32] = 64; b[54] = 56; b[56] = 1
        b[64] = 1 // PT_LOAD
        return b
    }

    @Test
    fun buildHasCoreFlagsAndNoOptionalEnv() {
        val d = tmp()
        try {
            val layout = DebianLayout(d)
            val l = ProotLauncher.build(layout, d.path)
            assertEquals(layout.prootBin.path, l.command.first())
            assertTrue(l.command.contains("--link2symlink"))
            assertTrue(l.command.contains("-0"))
            assertEquals(layout.rootfsDir.path, l.command[l.command.indexOf("-r") + 1])
            assertTrue(l.command.contains("/storage/emulated/0:/sdcard"))
            assertEquals("/bin/bash", l.command[l.command.size - 2])
            assertEquals("--login", l.command.last())
            assertEquals(layout.prootTmp.path, l.env["PROOT_TMP_DIR"])
            assertNull(l.env["PROOT_LOADER"])
            assertNull(l.env["LD_LIBRARY_PATH"])
            assertNull(l.env["PROOT_NO_SECCOMP"])
            assertEquals("1", ProotLauncher.build(layout, d.path, noSeccomp = true).env["PROOT_NO_SECCOMP"])
        } finally {
            d.deleteRecursively()
        }
    }

    @Test
    fun buildAddsLoaderAndLibraryPathWhenPresent() {
        val d = tmp()
        try {
            val layout = DebianLayout(d)
            layout.prootDir.mkdirs()
            File(layout.prootDir, "loader").writeText("x")
            File(layout.prootDir, "libtalloc.so.2").writeText("x")
            val l = ProotLauncher.build(layout, d.path)
            assertEquals(File(layout.prootDir, "loader").path, l.env["PROOT_LOADER"])
            assertEquals(layout.prootDir.path, l.env["LD_LIBRARY_PATH"])
        } finally {
            d.deleteRecursively()
        }
    }

    @Test
    fun buildAddsTermuxStyleBindsSysvipcAndL2sDir() {
        val d = tmp()
        try {
            val layout = DebianLayout(d)
            val l = ProotLauncher.build(
                layout, d.path,
                lexists = { p -> p == "/dev/urandom" || p == "/proc/self/fd" || p == "/proc/self/fd/0" || p == "/proc/self/fd/1" },
            )
            assertTrue(l.command.contains("--sysvipc"))
            assertTrue(l.command.contains("/dev/urandom:/dev/random"))
            assertTrue(l.command.contains("/proc/self/fd:/dev/fd"))
            assertTrue(l.command.contains("/proc/self/fd/0:/dev/stdin"))
            assertTrue(l.command.contains("/proc/self/fd/1:/dev/stdout"))
            assertFalse(l.command.contains("/proc/self/fd/2:/dev/stderr"))
            assertTrue(l.command.contains("${layout.prootShm.path}:/dev/shm"))
            assertTrue(layout.prootShm.isDirectory)
            assertEquals(File(layout.rootfsDir, ".l2s").path, l.env["PROOT_L2S_DIR"])
            val none = ProotLauncher.build(layout, d.path, lexists = { true })
            assertFalse(none.command.contains("/proc/self/fd:/dev/fd"))
        } finally {
            d.deleteRecursively()
        }
    }

    @Test
    fun prepareGuestIsIdempotentAndKeepsUserEdits() {
        val d = tmp()
        try {
            File(d, "etc/apt/apt.conf.d").mkdirs()
            ProotLauncher.prepareGuest(d)
            assertTrue(File(d, "etc/resolv.conf").readText().contains("nameserver"))
            assertTrue(File(d, "etc/apt/apt.conf.d/01molina-proot").readText().contains("Sandbox"))
            assertTrue(File(d, "sdcard").isDirectory)
            File(d, "etc/resolv.conf").writeText("nameserver 9.9.9.9\n")
            ProotLauncher.prepareGuest(d)
            assertEquals("nameserver 9.9.9.9\n", File(d, "etc/resolv.conf").readText())
        } finally {
            d.deleteRecursively()
        }
    }

    @Test
    fun importProotCopiesValidFolderAndRejectsWrongArch() {
        val src = tmp()
        val files = tmp()
        try {
            val layout = DebianLayout(files)
            val bad = elfArm64Static().also { it[18] = 62 }
            File(src, "proot").writeBytes(bad)
            assertFalse(layout.importProot(src).ok)
            assertFalse(layout.prootBin.exists())

            File(src, "proot").writeBytes(elfArm64Static())
            File(src, "libtalloc.so.2").writeText("x")
            assertTrue(layout.importProot(src).ok)
            assertTrue(layout.isProotReady())
            assertTrue(File(layout.prootDir, "libtalloc.so.2").isFile)
        } finally {
            src.deleteRecursively()
            files.deleteRecursively()
        }
    }
}
