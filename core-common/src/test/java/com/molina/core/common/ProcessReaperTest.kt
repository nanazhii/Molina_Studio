package com.molina.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class ProcessReaperTest {
    private fun sh(script: String): Process {
        assumeTrue(File("/bin/sh").canExecute())
        return ProcessBuilder("/bin/sh", "-c", script).redirectErrorStream(true).start()
    }

    @Test fun menghentikanProsesBiasa() {
        val p = sh("exec sleep 30")
        assertTrue(ProcessReaper.terminate(p, 1_000))
        assertFalse(p.isAlive)
    }

    @Test fun memaksaBilaSigtermDiabaikan() {
        val p = sh("trap '' TERM; while true; do sleep 1; done")
        Thread.sleep(150) // beri waktu trap terpasang
        val t0 = System.nanoTime()
        assertTrue(ProcessReaper.terminate(p, 400))
        assertFalse(p.isAlive)
        assertTrue("harus lekas", (System.nanoTime() - t0) / 1_000_000 < 3_000)
    }

    @Test fun prosesYangSudahMatiAman() {
        val p = sh("exit 0")
        p.waitFor()
        assertTrue(ProcessReaper.terminate(p, 200))
    }
}
