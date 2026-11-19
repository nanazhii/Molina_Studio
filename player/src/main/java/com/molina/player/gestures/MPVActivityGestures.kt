package com.molina.player.gestures

import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.View
import com.molina.core.common.diag.Diag
import com.molina.player.PropertyChange
import com.molina.player.R
import com.molina.player.TouchGesturesObserver
import com.molina.player.Utils
import com.molina.player.audio.MPVActivityAudioPresence
import com.molina.player.display.MPVActivityBrightness
import kotlin.math.roundToInt

/**
 * Penerjemah gestur sentuh ke aksi pemutar: geser untuk seek/volume/kecerahan, ketuk dua kali untuk lompat/jeda/tombol kustom.
 * Tanggung jawab tunggal: mengubah [PropertyChange] dari TouchGestures menjadi perintah (lewat MPVView, AudioManager,
 * MPVActivityBrightness) dan menampilkan teks gestur. Hal milik layar datang lewat [MPVActivityGesturesHost].
 * Setiap gestur yang bertindak dicatat sebagai player.gesture (jenisnya di ringkasan).
 */
internal class MPVActivityGestures(
    private val host: MPVActivityGesturesHost,
    private val brightness: MPVActivityBrightness,
    private val audioPresence: MPVActivityAudioPresence,
) : TouchGesturesObserver {
    /** Nilai pengaturan seek_gesture_smooth; diisi layar saat membaca pengaturan. */
    var smoothSeek = false

    private var initialSeek = 0f
    private var initialBright = 0f
    private var initialVolume = 0
    private var maxVolume = 0

    /** 0 = initial, 1 = paused, 2 = was already paused */
    private var pausedForSeek = 0

    /** Jenis gestur seret yang sudah bertindak sejak Init; kosong bila belum ada. Dicatat saat Finalize. */
    private var kind = ""

    private val handler = Handler(Looper.getMainLooper())

    // okay this doesn't actually fade...
    private val hideText = Runnable { host.gestureTextView.visibility = View.GONE }

    private fun fadeText() {
        handler.removeCallbacks(hideText)
        host.gestureTextView.visibility = View.VISIBLE
        handler.postDelayed(hideText, 500L)
    }

    override fun onPropertyChange(p: PropertyChange, diff: Float) {
        val text = host.gestureTextView
        val psc = host.playbackState
        val player = host.player
        val context = host.context
        when (p) {
            /* Drag gestures */
            PropertyChange.Init -> {
                host.onGestureStart()

                initialSeek = (psc.position / 1000f)
                initialBright = brightness.current()
                val am = host.audioManager
                if (am != null) {
                    initialVolume = am.getStreamVolume(STREAM_TYPE)
                    maxVolume = if (am.isVolumeFixed)
                        0
                    else
                        am.getStreamMaxVolume(STREAM_TYPE)
                } else {
                    maxVolume = 0
                }
                if (!audioPresence.isPlaying)
                    maxVolume = 0 // disallow volume gesture if no audio
                pausedForSeek = 0
                kind = ""

                handler.removeCallbacks(hideText)
                text.visibility = View.VISIBLE
                text.text = ""
            }
            PropertyChange.Seek -> {
                // disable seeking when duration is unknown
                val duration = (psc.duration / 1000f)
                if (duration == 0f || initialSeek < 0)
                    return
                if (smoothSeek && pausedForSeek == 0) {
                    pausedForSeek = if (psc.pause) 2 else 1
                    if (pausedForSeek == 1)
                        player.paused = true
                }

                val newPosExact = (initialSeek + diff).coerceIn(0f, duration)
                val newPos = newPosExact.roundToInt()
                val newDiff = (newPosExact - initialSeek).roundToInt()
                if (smoothSeek) {
                    player.timePos = newPosExact.toDouble() // (exact seek)
                } else {
                    // seek faster than assigning to timePos but less precise
                    player.seekToKeyframe(newPosExact)
                }
                kind = if (smoothSeek) "seek-smooth" else "seek"
                // Note: don't update the seekbar here because mpv will seek a timestamp
                // actually present in the file, and not the exact one we specified.

                val posText = Utils.prettyTime(newPos)
                val diffText = Utils.prettyTime(newDiff, true)
                text.text = context.getString(R.string.ui_seek_distance, posText, diffText)
            }
            PropertyChange.Volume -> {
                if (maxVolume == 0)
                    return
                val newVolume = (initialVolume + (diff * maxVolume).toInt()).coerceIn(0, maxVolume)
                val newVolumePercent = 100 * newVolume / maxVolume
                host.audioManager?.setStreamVolume(STREAM_TYPE, newVolume, 0)
                kind = "volume"

                text.text = context.getString(R.string.ui_volume, newVolumePercent)
            }
            PropertyChange.Bright -> {
                val newBrightPercent = ((initialBright + diff).coerceIn(0f, 1f) * 100).roundToInt()
                brightness.set(newBrightPercent)
                kind = "brightness"

                text.text = context.getString(R.string.ui_brightness, newBrightPercent)
            }
            PropertyChange.Finalize -> {
                if (pausedForSeek == 1)
                    player.paused = false
                if (kind.isNotEmpty())
                    Diag.behavior("player", "gesture", kind)
                text.visibility = View.GONE
            }

            /* Tap gestures */
            PropertyChange.SeekFixed -> {
                val seekTime = diff * 10f
                val newPos = psc.positionSec + seekTime.toInt() // only for display
                player.seekBy(seekTime)
                Diag.behavior("player", "gesture", "seek-tap", mapOf("sec" to seekTime.toInt().toString()))

                val diffText = Utils.prettyTime(seekTime.toInt(), true)
                text.text = context.getString(R.string.ui_seek_distance, Utils.prettyTime(newPos), diffText)
                fadeText()
            }
            PropertyChange.PlayPause -> player.cyclePause() // sudah tercatat oleh MpvGateway
            PropertyChange.Custom -> {
                val keycode = 0x10002 + diff.toInt()
                player.pressKey(keycode)
                Diag.behavior("player", "gesture", "custom-key", mapOf("key" to "0x%x".format(keycode)))
            }
        }
    }

    companion object {
        /** Aliran volume yang dikendalikan gestur dan tombol volume layar (satu-satunya definisi). */
        const val STREAM_TYPE = AudioManager.STREAM_MUSIC
    }
}
