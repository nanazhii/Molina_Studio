// molina-patch-b
package com.molina.player.mpv

import com.molina.cinema.AspectMode
import com.molina.cinema.PlayerLimits
import java.util.Locale

/** Konversi nilai kontrak -> nilai properti/perintah mpv. Murni, bisa diuji di JVM. */
internal object MpvOptions {
    fun aspect(mode: AspectMode): String = when (mode) {
        AspectMode.ORIGINAL -> "-1"
        AspectMode.RATIO_16_9 -> "16:9"
        AspectMode.RATIO_4_3 -> "4:3"
        AspectMode.RATIO_21_9 -> "21:9"
    }

    /** Argumen detik untuk `seek <detik> relative`, mis. "-10.000". */
    fun seekSeconds(deltaMs: Long): String = String.format(Locale.ROOT, "%.3f", deltaMs / 1000.0)

    /** audio-delay / sub-delay di mpv bertipe detik (double). */
    fun delaySeconds(ms: Long): Double = PlayerLimits.clampDelayMs(ms) / 1000.0
}
