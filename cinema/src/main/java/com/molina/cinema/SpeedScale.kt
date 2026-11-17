// molina-patch-c1
// Skala slider kecepatan diport dari SpeedPickerDialog repo lama (turunan mpv-android, GPL-3).
package com.molina.cinema

/**
 * Slider kecepatan non-linear: bagian bawah 0..100 memetakan 0.0x..1.0x, bagian atas bertambah 0.05x per langkah.
 * Hasil selalu dijepit ke PlayerLimits.MIN_SPEED..MAX_SPEED.
 */
internal object SpeedScale {
    private const val HALF = 100.0
    private const val SCALE_FACTOR = 20.0

    val MAX_PROGRESS: Int = fromSpeed(PlayerLimits.MAX_SPEED)

    fun toSpeed(progress: Int): Double {
        val raw = if (progress >= HALF) (progress - HALF) / SCALE_FACTOR + 1.0 else progress / HALF
        return PlayerLimits.clampSpeed(raw)
    }

    fun fromSpeed(speed: Double): Int {
        val s = PlayerLimits.clampSpeed(speed)
        return if (s >= 1.0) (HALF + (s - 1.0) * SCALE_FACTOR).toInt() else (HALF * s).toInt()
    }
}
