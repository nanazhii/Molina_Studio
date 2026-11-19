package com.molina.player.gestures

import android.content.Context
import android.media.AudioManager
import android.widget.TextView
import com.molina.player.MPVView
import com.molina.player.Utils

/**
 * Kontrak MPVActivityGestures ke layar pemutar. Hanya hal yang memang milik Activity:
 * konteks (string), pemutar, status putar, teks gestur, pengelola audio, dan pembatalan rencana toggle kontrol.
 */
internal interface MPVActivityGesturesHost {
    val context: Context
    val player: MPVView
    val playbackState: Utils.PlaybackStateCache
    val gestureTextView: TextView
    val audioManager: AudioManager?

    /** Gestur seret dimulai: sentuhan ini tidak boleh lagi dianggap ketukan untuk menampilkan/menyembunyikan kontrol. */
    fun onGestureStart()
}
