// molina-ytdlp-install
package com.molina.core.common.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class YtDlpInstallProtocolTest {
    private fun parse(s: String) = YtDlpInstallProtocol.parse(s)

    @Test fun tahapDikenali() {
        for (st in YtDlpInstallStage.values()) {
            assertEquals(YtDlpInstallProtocol.Event.Stage(st), parse("MOLINA_STEP " + st.name))
        }
    }

    @Test fun tahapTakDikenaliDiabaikan() {
        assertNull(parse("MOLINA_STEP BOGUS"))
        assertNull(parse("MOLINA_STEP"))
    }

    @Test fun selesaiMembawaVersi() {
        assertEquals(YtDlpInstallProtocol.Event.Done("2026.10.26"), parse("MOLINA_DONE 2026.10.26\r"))
    }

    @Test fun versiMencurigakanDikosongkan() {
        assertEquals(YtDlpInstallProtocol.Event.Done(""), parse("MOLINA_DONE 1.0; rm -rf /"))
        assertEquals(YtDlpInstallProtocol.Event.Done(""), parse("MOLINA_DONE " + "9".repeat(40)))
    }

    @Test fun kodeGagalDikenaliAtauJadiFailed() {
        assertEquals(YtDlpInstallProtocol.Event.Fail("BAD_HASH"), parse("MOLINA_FAIL BAD_HASH"))
        assertEquals(YtDlpInstallProtocol.Event.Fail("FAILED"), parse("MOLINA_FAIL " + "https" + "://x.example/rahasia"))
    }

    @Test fun barisBiasaDiabaikan() {
        assertNull(parse(""))
        assertNull(parse("Memasang python3..."))
        assertNull(parse("xMOLINA_DONE 1"))
    }

    @Test fun registryMenyimpanDanMencabut() {
        val fake = object : YtDlpInstaller {
            override fun isInstalled() = true
            override suspend fun install(onStage: (YtDlpInstallStage) -> Unit): YtDlpInstallResult =
                YtDlpInstallResult.Done("x")
        }
        YtDlpInstallerRegistry.install(fake)
        assertSame(fake, YtDlpInstallerRegistry.get())
        YtDlpInstallerRegistry.install(null)
        assertNull(YtDlpInstallerRegistry.get())
    }
}
