// molina-27m
package com.molina.filemanager

import com.molina.filemanager.EmbeddedMenu.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class Embedded27mTest {
    @Test fun ekstensiVideoDanAudioDikenali() {
        for (n in listOf("a.mp4", "b.MKV", "c.webm", "d.m2ts", "e.flac", "f.Mp3", "g.opus", "h.mka")) assertTrue(n, MediaExt.isPlayableName(n))
    }

    @Test fun bukanMediaTidakDikenali() {
        for (n in listOf("a.txt", "b.jpg", "c.srt", "d.zip", "e.m3u8", "Makefile", ".mp4", "x.", "arsip.tar.gz")) assertFalse(n, MediaExt.isPlayableName(n))
    }

    @Test fun extOfHurufKecilDanTitikAwalBukanEkstensi() {
        assertEquals("mkv", MediaExt.extOf("Film.Final.MKV"))
        assertNull(MediaExt.extOf(".hidden"))
        assertNull(MediaExt.extOf("tanpa"))
    }

    @Test fun tsKecilDianggapKodeTsBesarDianggapVideo() {
        val d = Files.createTempDirectory("m27m").toFile()
        try {
            val small = File(d, "app.ts").apply { writeText("export const a = 1") }
            val big = File(d, "klip.ts").apply { writeBytes(ByteArray(MediaExt.TS_MIN_BYTES.toInt())) }
            val mp4 = File(d, "x.mp4").apply { writeText("x") }
            assertFalse(MediaExt.isPlayable(small))
            assertTrue(MediaExt.isPlayable(big))
            assertTrue(MediaExt.isPlayable(mp4))
            assertFalse(MediaExt.isPlayable(File(d, "folder.mp4").apply { mkdir() }))
        } finally {
            d.deleteRecursively()
        }
    }

    @Test fun menuPutarMunculHanyaUntukSatuTargetMedia() {
        fun menu(count: Int, play: Boolean, selecting: Boolean = false) = EmbeddedMenu.build(
            EmbeddedMenu.State(count, true, true, false, true, global = selecting, selecting = selecting, playAvailable = play),
        ).map { it.action }
        assertEquals(Action.PLAY, menu(1, true).first())
        assertEquals(Action.PLAY, menu(1, true, selecting = true).first())
        assertFalse(Action.PLAY in menu(1, false))
        assertFalse(Action.PLAY in menu(2, true, selecting = true))
        assertFalse(Action.PLAY in EmbeddedMenu.build(EmbeddedMenu.State(0, true, true, false, true, global = true)).map { it.action })
    }

    @Test fun putarPunyaGlyphSendiri() {
        val g = EmbeddedMenuIcons.glyph(EmbeddedMenu.Entry(Action.PLAY, "Putar"))
        assertEquals(EmbeddedMenuIcons.Glyph.PLAY, g)
        assertTrue(File("src/main/res/drawable/" + g.file + ".xml").isFile)
    }
}
