// molina-patch-c1
package com.molina.cinema

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaControlsTest {
    @Test fun speedScale_titikTengahAdalahSatuKali() {
        assertEquals(1.0, SpeedScale.toSpeed(100), 1e-9)
        assertEquals(100, SpeedScale.fromSpeed(1.0))
    }

    @Test fun speedScale_batasBawahDanAtasDijepit() {
        assertEquals(PlayerLimits.MIN_SPEED, SpeedScale.toSpeed(0), 1e-9)
        assertEquals(PlayerLimits.MAX_SPEED, SpeedScale.toSpeed(10_000), 1e-9)
        assertEquals(PlayerLimits.MAX_SPEED, SpeedScale.toSpeed(SpeedScale.MAX_PROGRESS), 1e-9)
    }

    @Test fun speedScale_bolakBalikStabil() {
        for (s in listOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 3.0)) {
            assertEquals(s, SpeedScale.toSpeed(SpeedScale.fromSpeed(s)), 0.051)
        }
    }

    @Test fun speedScale_nanKembaliSatu() {
        assertEquals(100, SpeedScale.fromSpeed(Double.NaN))
    }

    @Test fun waktu_formatMenitDanJam() {
        assertEquals("0:00", CinemaFormat.time(0))
        assertEquals("0:00", CinemaFormat.time(-5))
        assertEquals("1:05", CinemaFormat.time(65_000))
        assertEquals("1:01:01", CinemaFormat.time(3_661_000))
    }

    @Test fun kecepatanDanTunda_format() {
        assertEquals("1.50x", CinemaFormat.speed(1.5))
        assertEquals("+100 ms", CinemaFormat.delay(100))
        assertEquals("-250 ms", CinemaFormat.delay(-250))
    }

    @Test fun rasio_berputarLengkap() {
        var m = AspectMode.ORIGINAL
        val seen = HashSet<AspectMode>()
        repeat(AspectMode.entries.size) { seen.add(m); m = CinemaFormat.nextAspect(m) }
        assertEquals(AspectMode.ORIGINAL, m)
        assertTrue(seen.size == AspectMode.entries.size)
    }
}
