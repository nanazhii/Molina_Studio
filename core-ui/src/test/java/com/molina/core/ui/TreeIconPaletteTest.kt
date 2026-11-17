// molina-29
package com.molina.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeIconPaletteTest {
    @Test fun lambangOtherMemakaiAbuTepiKertas() {
        assertEquals(TreeIconPalette.PAPER_EDGE, TreeIconPalette.glyphTint(FileKind.OTHER))
    }

    @Test fun lambangJenisLebihGelapDariWarnaJenisDanOpak() {
        for (k in FileKind.values()) {
            if (k == FileKind.OTHER) continue
            val t = TreeIconPalette.glyphTint(k)
            assertEquals(0xFF, (t shr 24) and 0xFF)
            for (shift in intArrayOf(16, 8, 0)) {
                assertTrue("$k saluran $shift", ((t shr shift) and 0xFF) <= ((k.color shr shift) and 0xFF))
            }
        }
    }

    @Test fun darkenFaktorSatuTidakMengubahWarna() {
        assertEquals(0xFF336699.toInt(), TreeIconPalette.darken(0xFF336699.toInt(), 1f))
    }
}
