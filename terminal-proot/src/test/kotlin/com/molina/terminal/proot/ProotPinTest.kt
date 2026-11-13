// molina-patch11
package com.molina.terminal.proot

import org.junit.Assert.assertTrue
import org.junit.Test

class ProotPinTest {
    @Test
    fun pinTerisiAtauKosongSecaraKonsisten() {
        val hex = Regex("[0-9a-f]{64}")
        if (ProotPin.CONFIGURED) {
            assertTrue(hex.matches(ProotPin.TGZ_SHA256))
            assertTrue(hex.matches(ProotPin.BIN_SHA256))
            assertTrue(ProotPin.URL.startsWith("https://"))
            assertTrue(ProotPin.SIZE_BYTES > 0)
        } else {
            assertTrue(ProotPin.URL.isEmpty() && ProotPin.TGZ_SHA256.isEmpty() && ProotPin.BIN_SHA256.isEmpty())
        }
    }
}
