package com.molina.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSizeFormatTest {
    @Test
    fun bytesBelowOneKilobyte() {
        assertEquals("0 B", FileSizeFormat.format(0))
        assertEquals("1023 B", FileSizeFormat.format(1023))
    }

    @Test
    fun kilobytesMegabytesGigabytes() {
        assertEquals("1.0 KB", FileSizeFormat.format(1024))
        assertEquals("1.5 KB", FileSizeFormat.format(1536))
        assertEquals("1.0 MB", FileSizeFormat.format(1024L * 1024))
        assertEquals("5.0 GB", FileSizeFormat.format(5L * 1024 * 1024 * 1024))
    }

    @Test
    fun hugeValueDoesNotOverflowUnits() {
        assertTrue(FileSizeFormat.format(Long.MAX_VALUE).endsWith(" TB"))
    }
}
