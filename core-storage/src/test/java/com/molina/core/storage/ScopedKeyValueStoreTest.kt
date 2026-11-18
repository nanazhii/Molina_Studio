// molina-patch26e2
package com.molina.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopedKeyValueStoreTest {
    private val base = MemoryKeyValueStore()

    @Test fun scopeBerbedaTidakSalingMembaca() {
        val molina = ScopedKeyValueStore(base, "/sdcard/Molina")
        val penuh = ScopedKeyValueStore(base, "/sdcard")
        molina.putString("recent_v1", "media")
        assertEquals("media", molina.getString("recent_v1"))
        assertNull(penuh.getString("recent_v1"))
    }

    @Test fun scopeSamaMembacaDataYangSama() {
        ScopedKeyValueStore(base, "/r").putBoolean("x", true)
        assertTrue(ScopedKeyValueStore(base, "/r").getBoolean("x", false))
        assertFalse(ScopedKeyValueStore(base, "/lain").getBoolean("x", false))
    }

    @Test fun riwayatTerakhirTerikatKeRoot() {
        ExplorerRecent(ScopedKeyValueStore(base, "/sdcard/Molina")).push("media")
        assertEquals(emptyList<String>(), ExplorerRecent(ScopedKeyValueStore(base, "/sdcard")).paths())
        assertEquals(listOf("media"), ExplorerRecent(ScopedKeyValueStore(base, "/sdcard/Molina")).paths())
    }
}
