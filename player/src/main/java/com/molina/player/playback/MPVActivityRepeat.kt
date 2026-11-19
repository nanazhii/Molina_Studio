package com.molina.player.playback

import android.support.v4.media.session.PlaybackStateCompat
import com.molina.player.MPVView
import com.molina.player.mpv.MpvProperties

/**
 * Pemetaan mode ulang dan acak antara MediaSession (tombol notifikasi, headset) dan mpv.
 * Tanggung jawab tunggal: menerjemahkan nilai, tanpa menyentuh UI. Penulisan property lewat MpvProperties.
 */
internal class MPVActivityRepeat(private val playerProvider: () -> MPVView) {
    private val player get() = playerProvider()

    /** Dari MediaSession ke mpv. */
    fun applyRepeat(repeatMode: Int) {
        MpvProperties.setString("loop-playlist", if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ALL) "inf" else "no")
        MpvProperties.setString("loop-file", if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE) "inf" else "no")
    }

    /** Dari MediaSession ke mpv. */
    fun applyShuffle(shuffleMode: Int) {
        player.changeShuffle(false, shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL)
    }

    /** Dari mpv ke nilai MediaSession (membaca loop-file dan loop-playlist). */
    fun sessionRepeatMode(): Int = when (player.getRepeat()) {
        2 -> PlaybackStateCompat.REPEAT_MODE_ONE
        1 -> PlaybackStateCompat.REPEAT_MODE_ALL
        else -> PlaybackStateCompat.REPEAT_MODE_NONE
    }

    fun sessionShuffleMode(shuffled: Boolean): Int =
        if (shuffled) PlaybackStateCompat.SHUFFLE_MODE_ALL else PlaybackStateCompat.SHUFFLE_MODE_NONE
}
