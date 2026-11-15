// molina-patch26k5e:f02
package com.molina.files

import com.molina.core.storage.ArchiveEntryInfo
import com.molina.core.storage.ArchiveTree
import com.molina.core.ui.FileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveIconsTest {
    private val tree = ArchiveTree.build(
        listOf(
            ArchiveEntryInfo("Film/Ep1.MKV", 10, false),
            ArchiveEntryInfo("Film/sub.srt", 1, false),
            ArchiveEntryInfo("README", 1, false),
            ArchiveEntryInfo("x.tar.gz", 5, false),
        ),
    )

    private fun node(folder: String, name: String) = tree.children(folder).first { it.name == name }

    @Test fun setiapJenisPunyaGlyphTidakKosong() {
        for (k in FileKind.values()) assertTrue(k.name, ArchiveIcons.glyph(k).isNotEmpty())
    }

    @Test fun glyphVideoMemakaiPenandaGayaTeks() {
        assertTrue(ArchiveIcons.glyph(FileKind.VIDEO).endsWith("\uFE0E"))
    }

    @Test fun jenisBerbedaPunyaGlyphSesuaiKelompok() {
        assertEquals(ArchiveIcons.glyph(FileKind.TEXT), ArchiveIcons.glyph(FileKind.SUBTITLE))
        assertFalse(ArchiveIcons.glyph(FileKind.VIDEO) == ArchiveIcons.glyph(FileKind.AUDIO))
        assertFalse(ArchiveIcons.glyph(FileKind.IMAGE) == ArchiveIcons.glyph(FileKind.OTHER))
    }

    @Test fun folderMemakaiLambangFolder() {
        assertEquals(ArchiveIcons.FOLDER_GLYPH, ArchiveIcons.glyph(node("", "Film")))
    }

    @Test fun berkasMengikutiFileKindBersama() {
        assertEquals(ArchiveIcons.glyph(FileKind.VIDEO), ArchiveIcons.glyph(node("Film/", "Ep1.MKV")))
        assertEquals(ArchiveIcons.glyph(FileKind.SUBTITLE), ArchiveIcons.glyph(node("Film/", "sub.srt")))
        assertEquals(ArchiveIcons.glyph(FileKind.ARCHIVE), ArchiveIcons.glyph(node("", "x.tar.gz")))
        assertEquals(ArchiveIcons.glyph(FileKind.OTHER), ArchiveIcons.glyph(node("", "README")))
    }

    @Test fun warnaFolderDanJenisTakDikenal() {
        val folderColor = 0x11
        val plainColor = 0x22
        assertEquals(folderColor, ArchiveIcons.iconColor(node("", "Film"), folderColor, plainColor))
        assertEquals(folderColor, ArchiveIcons.nameColor(node("", "Film"), folderColor, plainColor))
        assertEquals(plainColor, ArchiveIcons.iconColor(node("", "README"), folderColor, plainColor))
        assertEquals(plainColor, ArchiveIcons.nameColor(node("", "README"), folderColor, plainColor))
    }

    @Test fun warnaBerkasDikenalDariFileKind() {
        assertEquals(FileKind.VIDEO.color, ArchiveIcons.iconColor(node("Film/", "Ep1.MKV"), 0x11, 0x22))
        assertEquals(FileKind.VIDEO.nameColor, ArchiveIcons.nameColor(node("Film/", "Ep1.MKV"), 0x11, 0x22))
    }

    @Test fun hanyaFormatYangBisaDidecodeDipratinjau() {
        assertTrue(ImagePreview.isImage("a.png"))
        assertTrue(ImagePreview.isImage("Foto.JPG"))
        assertTrue(ImagePreview.isImage("a.webp"))
        assertFalse(ImagePreview.isImage("a.svg"))
        assertFalse(ImagePreview.isImage("a.heic"))
        assertFalse(ImagePreview.isImage("a.mkv"))
        assertFalse(ImagePreview.isImage("png"))
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

    @Test fun barisKuotaCache() {
        val mb = 1024L * 1024L
        assertEquals("Cache arsip 0 / 300 MB", ArchiveViewText.cacheLine(0L, 300 * mb))
        assertEquals("Cache arsip <1 / 300 MB", ArchiveViewText.cacheLine(1024L, 300 * mb))
        assertEquals("Cache arsip 120 / 300 MB", ArchiveViewText.cacheLine(120 * mb, 300 * mb))
    }

    @Test fun labelKlipSebagian() {
        assertEquals("Ekstrak arsip ke\u2026", ArchiveText.clipLabel(1))
        assertEquals("Ekstrak 3 arsip ke\u2026", ArchiveText.clipLabel(3))
        assertEquals("Ekstrak 3 item terpilih ke\u2026", ArchiveText.clipLabel(1, 3))
    }
}
