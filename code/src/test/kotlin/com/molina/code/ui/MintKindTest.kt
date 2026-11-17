package com.molina.code.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MintKindTest {
    @Test fun ekstensiDikenali() {
        assertEquals(MintKind.SHELL, MintKind.of("run.sh"))
        assertEquals(MintKind.CODE, MintKind.of("Main.kt"))
        assertEquals(MintKind.MARKUP, MintKind.of("index.HTML"))
        assertEquals(MintKind.DATA, MintKind.of("a.json"))
        assertEquals(MintKind.VIDEO, MintKind.of("film.mkv"))
        assertEquals(MintKind.SUBTITLE, MintKind.of("film.srt"))
        assertEquals(MintKind.TEXT, MintKind.of("progres.md"))
    }

    @Test fun ekstensiTerakhirMenentukan() {
        assertEquals(MintKind.ARCHIVE, MintKind.of("skrip.tar.gz"))
    }

    @Test fun tanpaEkstensiAtauTersembunyiAdalahOther() {
        assertEquals(MintKind.OTHER, MintKind.of("README"))
        assertEquals(MintKind.OTHER, MintKind.of(".bashrc"))
        assertEquals(MintKind.OTHER, MintKind.of("akhir."))
        assertEquals(MintKind.OTHER, MintKind.of("a.tidakdikenal"))
    }

    @Test fun warnaNamaOtherPutihPolos() {
        assertEquals(0xFFFFFFFF.toInt(), MintKind.OTHER.nameArgb)
    }

    @Test fun warnaNamaCondongKeJenisTapiTetapOpak() {
        val c = MintKind.SHELL.nameArgb
        assertEquals(0xFF, (c shr 24) and 0xFF)
        assertNotEquals(0xFFFFFFFF.toInt(), c)
        // saluran merah turun paling jauh dari putih untuk hijau Molina (#00E676)
        assertEquals(true, ((c shr 16) and 0xFF) < ((c shr 8) and 0xFF))
    }
}
