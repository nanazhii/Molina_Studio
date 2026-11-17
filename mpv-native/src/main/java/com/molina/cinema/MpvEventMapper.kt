// molina-patch13e
package com.molina.cinema

import kotlin.math.abs
import kotlin.math.roundToLong

// molina-patch-a: Position diteruskan bila bergeser >= 200 ms dari yang terakhir dikirim.
private const val POSITION_STEP_MS = 200L
private const val NO_POSITION = -1L

/**
 * Menerjemahkan event/properti mpv mentah menjadi PlayerEvent. Tidak menyentuh MPVLib sehingga
 * aman diuji di JVM. Dipanggil dari thread event mpv; keadaan ditandai @Volatile.
 *
 * Aturan: Position dan perubahan pause hanya diteruskan setelah berkas dimuat; END_FILE tanpa
 * FILE_LOADED setelah START_FILE dianggap gagal memuat (rincian ada di log libmpv, mpv.native).
 */
class MpvEventMapper {
    @Volatile private var uri = ""
    @Volatile private var durationMs = 0L
    @Volatile private var paused = false
    @Volatile private var loaded = false
    @Volatile private var starting = false
    @Volatile private var lastPosMs = NO_POSITION

    fun onLoadRequested(uri: String) { this.uri = uri }

    fun onDuration(seconds: Double) {
        durationMs = if (seconds.isNaN() || seconds.isInfinite() || seconds < 0.0) 0L else (seconds * 1000.0).roundToLong()
    }

    /** Posisi detik (Double) -> ms. Nilai pertama setelah berkas dimuat dan setiap lompatan (seek) >= 200 ms selalu lolos. */
    fun onTimePos(seconds: Double): List<PlayerEvent> {
        if (!loaded || seconds.isNaN() || seconds.isInfinite()) return emptyList()
        val ms = (seconds * 1000.0).roundToLong().coerceAtLeast(0L)
        val last = lastPosMs
        if (last != NO_POSITION && abs(ms - last) < POSITION_STEP_MS) return emptyList()
        lastPosMs = ms
        return listOf(PlayerEvent.Position(ms, durationMs))
    }

    fun onPause(value: Boolean): List<PlayerEvent> {
        paused = value
        return if (loaded) listOf(PlayerEvent.StateChanged(if (value) PlayerState.PAUSED else PlayerState.PLAYING)) else emptyList()
    }

    fun onEof(value: Boolean): List<PlayerEvent> {
        if (!value || !loaded) return emptyList()
        loaded = false
        return listOf(PlayerEvent.Ended, PlayerEvent.StateChanged(PlayerState.ENDED))
    }

    fun onEvent(id: Int): List<PlayerEvent> = when (id) {
        MPVLib.MpvEvent.MPV_EVENT_START_FILE -> {
            loaded = false
            starting = true
            lastPosMs = NO_POSITION
            listOf(PlayerEvent.StateChanged(PlayerState.LOADING))
        }
        MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
            loaded = true
            starting = false
            lastPosMs = NO_POSITION
            listOf(
                PlayerEvent.Loaded(uri),
                PlayerEvent.StateChanged(if (paused) PlayerState.PAUSED else PlayerState.PLAYING),
            )
        }
        MPVLib.MpvEvent.MPV_EVENT_END_FILE ->
            if (starting && !loaded) {
                starting = false
                listOf(PlayerEvent.Error(-1, "berkas gagal dimuat"), PlayerEvent.StateChanged(PlayerState.ERROR))
            } else {
                emptyList()
            }
        else -> emptyList()
    }
}
