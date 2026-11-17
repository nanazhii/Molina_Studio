package com.molina.terminal.proot

import com.molina.core.security.LocalShieldProxy
import com.molina.core.security.ShieldConfig
import com.molina.log.LogEntry
import com.molina.log.LogSink
import com.molina.log.MolinaLog
import com.molina.log.MolinaLogger
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShieldGuestEnvTest {
    private val dir: File = Files.createTempDirectory("molina-shield").toFile()
    private var shield: LocalShieldProxy? = null
    private val lines = ArrayList<String>()

    private fun script() = File(dir, ShieldGuestEnv.SCRIPT_PATH)

    private fun shield(cfg: ShieldConfig = ShieldConfig(), start: Boolean = true): LocalShieldProxy =
        LocalShieldProxy(cfg).also { if (start) assertTrue(it.start()); shield = it }

    @After fun bersih() { shield?.stop(); dir.deleteRecursively() }

    @Test fun optInMatiMenghapusBerkas() {
        val sh = shield()
        assertEquals(ShieldGuestEnv.Mode.PROXY, ShieldGuestEnv.install(dir, sh, optIn = true))
        assertTrue(script().isFile)
        assertEquals(ShieldGuestEnv.Mode.OFF, ShieldGuestEnv.install(dir, sh, optIn = false))
        assertFalse(script().exists())
    }

    @Test fun proxyMenulisVariabelDenganLoopbackDikecualikan() {
        val sh = shield()
        ShieldGuestEnv.install(dir, sh, optIn = true)
        val t = script().readText()
        val url = sh.endpoint()!!.url()
        assertTrue(t.contains("export http_proxy='$url' https_proxy='$url'"))
        assertTrue(t.contains("no_proxy='localhost,127.0.0.1,::1'"))
        assertFalse(File(dir, ShieldGuestEnv.SCRIPT_PATH + ".tmp").exists())
    }

    @Test fun failClosedMengarahkanKePortMati() {
        val sh = shield(start = false)           // menyala tetapi belum berjalan
        assertEquals(ShieldGuestEnv.Mode.FAIL_CLOSED, ShieldGuestEnv.install(dir, sh, optIn = true))
        assertTrue(script().readText().contains(":9'"))
    }

    @Test fun penandaModeDitulisUntukPrompt() {
        val sh = shield()
        ShieldGuestEnv.install(dir, sh, optIn = true)
        assertTrue(script().readText().contains("export MOLINA_SHIELD='proxy'"))
        val sh2 = shield(start = false)
        ShieldGuestEnv.install(dir, sh2, optIn = true)
        assertTrue(script().readText().contains("export MOLINA_SHIELD='fail-closed'"))
    }

    @Test fun shieldDimatikanPenggunaTidakMemakaiProxy() {
        val sh = shield(ShieldConfig(enabled = false))
        assertEquals(ShieldGuestEnv.Mode.OFF, ShieldGuestEnv.install(dir, sh, optIn = true))
        assertFalse(script().exists())
        assertEquals(ShieldGuestEnv.Mode.OFF, ShieldGuestEnv.install(dir, null, optIn = true))
    }

    @Test fun tokenBerubahDanBerkasDitimpa() {
        val sh = shield()
        ShieldGuestEnv.install(dir, sh, optIn = true)
        val first = script().readText()
        sh.stop(); assertTrue(sh.start())
        ShieldGuestEnv.install(dir, sh, optIn = true)
        assertTrue(script().readText() != first)
    }

    @Test fun logTidakMemuatToken() {
        MolinaLog.install(MolinaLogger(listOf(object : LogSink { override fun write(entry: LogEntry) { lines.add(entry.message) } })))
        val sh = shield()
        ShieldGuestEnv.install(dir, sh, optIn = true)
        assertTrue(lines.any { it.contains("term.shield-env") && it.contains("mode=proxy") })
        assertTrue(lines.none { it.contains(sh.endpoint()!!.token) })
    }

    @Test fun urlTakAmanDitolak() {
        var failed = false
        try { ShieldGuestEnv.scriptText("x'; rm -rf / #") } catch (_: IllegalArgumentException) { failed = true }
        assertTrue(failed)
    }
}
