// molina-patch26k5d
package com.molina.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveIconsTest {
    @Test fun jenisDariEkstensiTanpaMembedakanHurufBesar() {
        assertEquals(FileKind.VIDEO, ArchiveIcons.kindOf("Film.MKV", false))
        assertEquals(FileKind.AUDIO, ArchiveIcons.kindOf("a.flac", false))
        assertEquals(FileKind.IMAGE, ArchiveIcons.kindOf("poster.JPG", false))
        assertEquals(FileKind.TEXT, ArchiveIcons.kindOf("sub.srt", false))
        assertEquals(FileKind.ARCHIVE, ArchiveIcons.kindOf("x.tar.gz", false))
        assertEquals(FileKind.OTHER, ArchiveIcons.kindOf("README", false))
        assertEquals(FileKind.OTHER, ArchiveIcons.kindOf(".bashrc", false))
    }

    @Test fun folderSelaluFolderWalauNamaBerekstensi() {
        assertEquals(FileKind.FOLDER, ArchiveIcons.kindOf("season.mkv", true))
    }

    @Test fun setiapJenisPunyaGlyphBerbeda() {
        val glyphs = FileKind.values().map { ArchiveIcons.glyph(it) }
        assertEquals(glyphs.size, glyphs.toSet().size)
    }

    @Test fun glyphVideoMemakaiPenandaGayaTeks() {
        assertTrue(ArchiveIcons.glyph(FileKind.VIDEO).endsWith("\uFE0E"))
    }

    @Test fun sampleSizePangkatDuaDanTidakMelebihiBatas() {
        assertEquals(1, ImagePreview.sampleSize(1000, 800, 2048))
        assertEquals(2, ImagePreview.sampleSize(4000, 3000, 2048))
        assertEquals(4, ImagePreview.sampleSize(8000, 6000, 2048))
        assertEquals(1, ImagePreview.sampleSize(0, 10, 2048))
        assertEquals(1, ImagePreview.sampleSize(10, 10, 0))
        val s = ImagePreview.sampleSize(12000, 9000, 2048)
        assertTrue(12000 / s <= 2048 && 9000 / s <= 2048)
    }

    @Test fun hanyaFormatGambarDipratinjau() {
        assertTrue(ImagePreview.isImage("a.png"))
        assertFalse(ImagePreview.isImage("a.mkv"))
        assertNotEquals(FileKind.IMAGE, ArchiveIcons.kindOf("a.txt", false))
    }

    @Test fun barisKuotaCache() {
        val mb = 1024L * 1024L
        assertEquals("Cache arsip 0 / 300 MB", ArchiveViewText.cacheLine(0L, 300 * mb))
        assertEquals("Cache arsip <1 / 300 MB", ArchiveViewText.cacheLine(1024L, 300 * mb))
        assertEquals("Cache arsip 120 / 300 MB", ArchiveViewText.cacheLine(120 * mb, 300 * mb))
    }

    @Test fun labelKlipSebagian() {
        assertEquals("Ekstrak arsip ke\u2026", ArchiveText.clipLabel(1))
        assertEquals("Ekstrak 3 item terpilih ke\u2026", ArchiveText.clipLabel(1, 3))
    }
}
