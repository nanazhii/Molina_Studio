// molina-patch-b
package com.molina.player.mpv

import com.molina.cinema.AspectMode
import com.molina.cinema.PlayerLimits
import org.junit.Assert.assertEquals
import org.junit.Test

class MpvOptionsTest {
    @Test
    fun aspectValues() {
        assertEquals("-1", MpvOptions.aspect(AspectMode.ORIGINAL))
        assertEquals("16:9", MpvOptions.aspect(AspectMode.RATIO_16_9))
        assertEquals("4:3", MpvOptions.aspect(AspectMode.RATIO_4_3))
        assertEquals("21:9", MpvOptions.aspect(AspectMode.RATIO_21_9))
    }

    @Test
    fun seekSecondsUsesDotAndSign() {
        assertEquals("-10.000", MpvOptions.seekSeconds(-10_000))
        assertEquals("1.500", MpvOptions.seekSeconds(1_500))
        assertEquals("0.250", MpvOptions.seekSeconds(250))
    }

    @Test
    fun delayIsClampedAndInSeconds() {
        assertEquals(0.5, MpvOptions.delaySeconds(500), 1e-9)
        assertEquals(60.0, MpvOptions.delaySeconds(1_000_000), 1e-9)
        assertEquals(-60.0, MpvOptions.delaySeconds(-1_000_000), 1e-9)
    }

    @Test
    fun speedIsClamped() {
        assertEquals(0.25, PlayerLimits.clampSpeed(0.1), 1e-9)
        assertEquals(4.0, PlayerLimits.clampSpeed(9.0), 1e-9)
        assertEquals(1.0, PlayerLimits.clampSpeed(Double.NaN), 1e-9)
        assertEquals(1.5, PlayerLimits.clampSpeed(1.5), 1e-9)
    }
}
