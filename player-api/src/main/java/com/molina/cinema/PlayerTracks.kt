// molina-patch-b
package com.molina.cinema

enum class TrackType { AUDIO, SUBTITLE }

/** Satu trek audio/subtitle. id = id trek mpv (bukan indeks daftar). */
data class TrackInfo(
    val id: Int,
    val type: TrackType,
    val lang: String?,
    val title: String?,
    val selected: Boolean,
    val codec: String? = null,
) {
    /** Label siap tampil: "judul · bahasa", bahasa saja, judul saja, atau "Trek N". */
    val displayName: String
        get() {
            val l = lang?.takeIf { it.isNotBlank() }
            val t = title?.takeIf { it.isNotBlank() }
            return when {
                t != null && l != null -> "$t · $l"
                l != null -> l
                t != null -> t
                else -> "Trek $id"
            }
        }
}

enum class AspectMode { ORIGINAL, RATIO_16_9, RATIO_4_3, RATIO_21_9 }

/** Batas nilai yang diterima sesi; nilai di luar batas dijepit, bukan ditolak. */
object PlayerLimits {
    const val MIN_SPEED = 0.25
    const val MAX_SPEED = 4.0
    const val MAX_DELAY_MS = 60_000L

    fun clampSpeed(v: Double): Double = if (v.isNaN()) 1.0 else v.coerceIn(MIN_SPEED, MAX_SPEED)
    fun clampDelayMs(v: Long): Long = v.coerceIn(-MAX_DELAY_MS, MAX_DELAY_MS)
}
