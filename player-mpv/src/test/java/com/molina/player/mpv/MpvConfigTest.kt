// molina-patch-a
package com.molina.player.mpv

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvConfigTest {
    private fun tempDir(): File = Files.createTempDirectory("mpvcfg").toFile()

    @Test
    fun fontsConfListsSystemFontsAndEscapesCacheDir() {
        val xml = MpvConfig.fontsConf("/data/a&b/<c>")
        assertTrue(xml.startsWith("<fontconfig>"))
        assertTrue(xml.endsWith("</fontconfig>"))
        assertTrue(xml.contains("<dir>/system/fonts/</dir>"))
        assertTrue(xml.contains("<cachedir>/data/a&amp;b/&lt;c&gt;</cachedir>"))
        assertTrue(xml.contains("<family>Roboto</family>"))
    }

    @Test
    fun writeIfChangedCreatesSkipsAndReplaces() {
        val d = tempDir()
        try {
            val f = File(d, "x.bin")
            assertTrue(MpvConfig.writeIfChanged(f, byteArrayOf(1, 2, 3)))
            assertTrue(f.readBytes().contentEquals(byteArrayOf(1, 2, 3)))
            assertTrue(MpvConfig.writeIfChanged(f, byteArrayOf(1, 2, 3)))
            assertTrue(MpvConfig.writeIfChanged(f, byteArrayOf(9)))
            assertTrue(f.readBytes().contentEquals(byteArrayOf(9)))
            assertFalse(File(d, "x.bin.tmp").exists())
        } finally {
            d.deleteRecursively()
        }
    }

    @Test
    fun writeIfChangedReportsFailureWhenDirectoryMissing() {
        val d = tempDir()
        try {
            assertFalse(MpvConfig.writeIfChanged(File(d, "tidak-ada/x.bin"), byteArrayOf(1)))
        } finally {
            d.deleteRecursively()
        }
    }

    @Test
    fun bundledCaBundleMatchesPin() {
        val f = File("src/main/assets/cacert.pem")
        assertTrue("cacert.pem tidak ada di aset mpv-native", f.isFile)
        val hex = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals("796f1389bfea13c0ed78f663b765baaff54a508e5483786bd6ece73c5e3e3cbe", hex)
        assertTrue(f.readText().contains("BEGIN CERTIFICATE"))
    }
}
