// molina-patch-c1
package com.molina.cinema

import java.util.Locale

/** Pemformat teks kontrol Cinema (murni, bisa diuji tanpa Android). */
internal object CinemaFormat {
    fun time(ms: Long): String {
        val s = (if (ms < 0) 0L else ms) / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.US, "%d:%02d", m, sec)
    }

    fun speed(v: Double): String = String.format(Locale.US, "%.2fx", v)

    fun delay(ms: Long): String = if (ms >= 0) "+$ms ms" else "$ms ms"

    fun aspectLabel(m: AspectMode): String = when (m) {
        AspectMode.ORIGINAL -> "Asli"
        AspectMode.RATIO_16_9 -> "16:9"
        AspectMode.RATIO_4_3 -> "4:3"
        AspectMode.RATIO_21_9 -> "21:9"
    }

    fun nextAspect(m: AspectMode): AspectMode = AspectMode.entries[(m.ordinal + 1) % AspectMode.entries.size]
}
