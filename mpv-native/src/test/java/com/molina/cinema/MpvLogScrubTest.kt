// molina-patch13
package com.molina.cinema

import org.junit.Assert.assertEquals
import org.junit.Test

class MpvLogScrubTest {
    @Test
    fun absolutePathBecomesFileName() {
        assertEquals("gagal membuka film.mkv", MpvLogScrub.scrub("gagal membuka /storage/emulated/0/Movies/film.mkv"))
    }

    @Test
    fun urlLosesHostAndQuery() {
        assertEquals("Error: film.mkv failed", MpvLogScrub.scrub("Error: https://h.example/x/film.mkv?token=rahasia failed"))
    }

    @Test
    fun plainTextIsUntouched() {
        assertEquals("Failed to recognize file format and/or codec", MpvLogScrub.scrub("Failed to recognize file format and/or codec"))
    }
}
