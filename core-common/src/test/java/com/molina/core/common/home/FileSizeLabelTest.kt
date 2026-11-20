package com.molina.core.common.home

import org.junit.Assert.assertEquals
import org.junit.Test

class FileSizeLabelTest {
    @Test fun satuan() {
        assertEquals("412 B", FileSizeLabel.of(412))
        assertEquals("7 KB", FileSizeLabel.of(7 * 1024 + 300))
        assertEquals("16 KB", FileSizeLabel.of(16 * 1024))
        assertEquals("1.5 MB", FileSizeLabel.of(1_572_864))
    }
}
