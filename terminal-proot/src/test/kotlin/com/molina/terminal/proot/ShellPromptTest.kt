// molina-patch15
package com.molina.terminal.proot

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellPromptTest {
    @Test
    fun envDefaultsToUserAndSwitchesToRoot() {
        assertTrue(ShellPrompt.envText(false).contains("MOLINA_PS_NAME='user'"))
        assertTrue(ShellPrompt.envText(true).contains("MOLINA_PS_NAME='root'"))
    }

    @Test
    fun requestCdWritesEpochAndPath() {
        val d = Files.createTempDirectory("molina").toFile()
        try {
            assertTrue(ShellPrompt.requestCd(d, "/sdcard/Molina_Studio", nowSec = 1_700_000_000L))
            assertEquals("1700000000\n/sdcard/Molina_Studio\n", File(d, ShellPrompt.CD_REQ_PATH).readText())
            assertEquals(false, ShellPrompt.requestCd(d, "/sdcard/a\nb"))
            assertEquals(false, ShellPrompt.requestCd(d, ""))
        } finally {
            d.deleteRecursively()
        }
    }

    @Test
    fun scriptHasSilentCdHook() {
        val s = ShellPrompt.script()
        assertTrue(s.contains("/etc/molina/cwd.req"))
        assertTrue(s.contains("CWD-HOOK"))
    }

    @Test
    fun scriptIsBundledWithColorRules() {
        val s = ShellPrompt.script()
        assertTrue(s.contains("__molina_ps1"))
        assertTrue(s.contains("/sdcard/Molina"))
        assertTrue(s.contains("@localhost:"))
    }

    @Test
    fun installWritesFilesAndIsIdempotent() {
        val d = Files.createTempDirectory("molina").toFile()
        try {
            ShellPrompt.install(d, false)
            val env = File(d, ShellPrompt.ENV_PATH)
            assertTrue(File(d, ShellPrompt.SCRIPT_PATH).isFile)
            assertEquals(ShellPrompt.envText(false), env.readText())
            env.setLastModified(1_000L)
            ShellPrompt.install(d, false)
            assertEquals(1_000L, env.lastModified())
            ShellPrompt.install(d, true)
            assertEquals(ShellPrompt.envText(true), env.readText())
        } finally {
            d.deleteRecursively()
        }
    }
}
