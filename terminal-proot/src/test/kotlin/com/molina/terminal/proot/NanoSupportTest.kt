// molina-patch21
package com.molina.terminal.proot

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NanoSupportTest {
    @Test
    fun scriptsAreBundledAndSpeakTheProtocol() {
        val wrapper = NanoSupport.resourceText("/molina/nano-wrapper.sh")
        assertTrue(wrapper.contains("nano: %s")) // judul sesi = NanoProtocol.TITLE_PREFIX
        assertTrue(wrapper.startsWith("#!/bin/sh"))
        val run = NanoSupport.resourceText("/molina/molina-run.sh")
        assertTrue(run.contains("nano-run: %s")) // = NanoProtocol.RUN_TITLE_PREFIX
        assertTrue(run.contains("Pilih [%s3/e/x]")) // format yang dibaca parseRunChoices
        assertTrue(NanoSupport.resourceText("/molina/nanorc").contains("bind M-r"))
    }

    @Test
    fun installWritesExecutablesAndIsIdempotent() {
        val d = Files.createTempDirectory("molina").toFile()
        try {
            assertEquals(NanoSupport.ASSETS.size, NanoSupport.install(d))
            val nano = File(d, "usr/local/bin/nano")
            assertTrue(nano.isFile && nano.canExecute())
            assertTrue(File(d, "usr/local/bin/molina-run").canExecute())
            assertEquals(0, NanoSupport.install(d))
            nano.writeText("rusak")
            assertEquals(1, NanoSupport.install(d))
            assertTrue(nano.readText().startsWith("#!/bin/sh"))
        } finally {
            d.deleteRecursively()
        }
    }
}
