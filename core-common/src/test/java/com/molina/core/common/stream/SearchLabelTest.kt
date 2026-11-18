package com.molina.core.common.stream

import com.molina.core.common.StreamSearchItem
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchLabelTest {
    @Test fun durasi() {
        assertEquals("0:00", SearchLabel.duration(0))
        assertEquals("0:59", SearchLabel.duration(59))
        assertEquals("1:05", SearchLabel.duration(65))
        assertEquals("59:59", SearchLabel.duration(3599))
        assertEquals("1:00:00", SearchLabel.duration(3600))
        assertEquals("2:03:04", SearchLabel.duration(7384))
        assertEquals("0:00", SearchLabel.duration(-5))
    }

    @Test fun labelLengkapDanMinimal() {
        val full = StreamSearchItem("p", " Judul ", "Kanal", 3725, null, "2 days ago")
        assertEquals("Judul\nKanal \u00B7 1:02:05 \u00B7 2 days ago", SearchLabel.of(full))
        assertEquals("Judul", SearchLabel.of(StreamSearchItem("p", "Judul")))
        assertEquals("(tanpa judul)", SearchLabel.of(StreamSearchItem("p", "  ")))
        assertEquals("J\nKanal", SearchLabel.of(StreamSearchItem("p", "J", uploader = "Kanal", durationSec = 0)))
        assertEquals("J", SearchLabel.of(StreamSearchItem("p", "J", uploader = " ", uploadedText = "")))
    }
}
