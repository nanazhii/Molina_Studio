// molina-patch26k5f:t01
package com.molina.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ArchiveExtractToLabelTest {
    @Test fun tanpaPilihanMengekstrakSemua() {
        assertEquals("Ekstrak semua ke\u2026", ArchiveViewText.extractToLabel(false))
    }

    @Test fun denganPilihanMengekstrakTerpilih() {
        assertEquals("Ekstrak terpilih ke\u2026", ArchiveViewText.extractToLabel(true))
    }

    @Test fun labelBerbedaAgarTidakAmbigu() {
        assertNotEquals(ArchiveViewText.extractToLabel(true), ArchiveViewText.extractToLabel(false))
    }
}
