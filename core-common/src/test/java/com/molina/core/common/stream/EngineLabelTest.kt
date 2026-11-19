package com.molina.core.common.stream

import org.junit.Assert.assertEquals
import org.junit.Test

class EngineLabelTest {
    @Test fun labelDikenal() {
        assertEquals("Langsung", EngineLabel.of(null))
        assertEquals("Langsung", EngineLabel.of(""))
        assertEquals("Langsung", EngineLabel.of("direct"))
        assertEquals("NewPipe", EngineLabel.of("newpipe"))
        assertEquals("NewPipe", EngineLabel.of(" NewPipe "))
        assertEquals("yt-dlp", EngineLabel.of("ytdlp"))
        assertEquals("yt-dlp", EngineLabel.of("yt-dlp"))
        assertEquals("Web", EngineLabel.of("generic"))
    }

    @Test fun idBaruTetapTampilDanDipotong() {
        assertEquals("kodi", EngineLabel.of("Kodi"))
        assertEquals("a".repeat(20), EngineLabel.of("a".repeat(40)))
    }
}
