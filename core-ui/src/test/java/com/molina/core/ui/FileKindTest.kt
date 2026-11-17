// molina-29
package com.molina.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileKindTest {
    @Test fun ekstensiDikenali() {
        assertEquals(FileKind.SHELL, FileKind.of("run.sh"))
        assertEquals(FileKind.CODE, FileKind.of("Main.kt"))
        assertEquals(FileKind.MARKUP, FileKind.of("index.HTML"))
        assertEquals(FileKind.DATA, FileKind.of("a.json"))
        assertEquals(FileKind.VIDEO, FileKind.of("film.mkv"))
        assertEquals(FileKind.SUBTITLE, FileKind.of("film.srt"))
        assertEquals(FileKind.TEXT, FileKind.of("progres.md"))
    }

    @Test fun ekstensiTerakhirMenentukan() {
        assertEquals(FileKind.ARCHIVE, FileKind.of("skrip.tar.gz"))
    }

    @Test fun tanpaEkstensiAtauTersembunyiAdalahOther() {
        assertEquals(FileKind.OTHER, FileKind.of("README"))
        assertEquals(FileKind.OTHER, FileKind.of(".bashrc"))
        assertEquals(FileKind.OTHER, FileKind.of("akhir."))
        assertEquals(FileKind.OTHER, FileKind.of("a.tidakdikenal"))
    }

    @Test fun warnaNamaOtherPutihPolos() {
        assertEquals(0xFFFFFFFF.toInt(), FileKind.OTHER.nameColor)
    }

    @Test fun warnaNamaCondongKeJenisTapiTetapOpak() {
        val c = FileKind.SHELL.nameColor
        assertEquals(0xFF, (c shr 24) and 0xFF)
        assertNotEquals(0xFFFFFFFF.toInt(), c)
        assertTrue(((c shr 16) and 0xFF) < ((c shr 8) and 0xFF)) // hijau Molina #00E676: merah turun paling jauh dari putih
    }
}
