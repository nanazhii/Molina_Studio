// molina-patch26k5g:t02
package com.molina.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExifOrientationTest {
    @Test fun normalTanpaKoreksi() {
        assertTrue(ExifOrientation.fix(ExifOrientation.NORMAL).isIdentity)
    }

    @Test fun nilaiTidakDikenalTanpaKoreksi() {
        for (v in intArrayOf(0, 9, 10, -1, Int.MAX_VALUE, Int.MIN_VALUE)) {
            assertEquals("v=$v", ExifOrientation.IDENTITY, ExifOrientation.fix(v))
        }
    }

    @Test fun tabelDelapanOrientasi() {
        assertEquals(ExifOrientation.Fix(0, false), ExifOrientation.fix(1))
        assertEquals(ExifOrientation.Fix(0, true), ExifOrientation.fix(2))
        assertEquals(ExifOrientation.Fix(180, false), ExifOrientation.fix(3))
        assertEquals(ExifOrientation.Fix(180, true), ExifOrientation.fix(4))
        assertEquals(ExifOrientation.Fix(90, true), ExifOrientation.fix(5))
        assertEquals(ExifOrientation.Fix(90, false), ExifOrientation.fix(6))
        assertEquals(ExifOrientation.Fix(270, true), ExifOrientation.fix(7))
        assertEquals(ExifOrientation.Fix(270, false), ExifOrientation.fix(8))
    }

    @Test fun fotoKameraPotretDiputar90() {
        val f = ExifOrientation.fix(6)
        assertEquals(90, f.degrees)
        assertFalse(f.flipX)
    }

    @Test fun hanyaLimaSampaiDelapanMenukarSumbu() {
        for (v in 1..8) {
            assertEquals("v=$v", v >= 5, ExifOrientation.fix(v).swapsAxes)
        }
    }

    @Test fun hanyaSatuIdentitasDiAntaraNilaiValid() {
        assertEquals(listOf(1), (1..8).filter { ExifOrientation.fix(it).isIdentity })
    }

    @Test fun semuaDelapanKoreksiBerbeda() {
        assertEquals(8, (1..8).map { ExifOrientation.fix(it) }.toSet().size)
    }
}
