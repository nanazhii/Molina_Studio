package com.molina.player.playback

import com.molina.core.common.diag.Diag
import com.molina.player.MPVView
import com.molina.player.StateRestoreCallback
import com.molina.player.audio.MPVActivityAudioPresence
import com.molina.player.mpv.MpvProperties

/**
 * Jeda pemutaran selama dialog terbuka.
 * Tanggung jawab tunggal: memilih cara (jeda biasa atau keep-open) menurut pengaturan no_ui_pause, menjalankannya
 * lewat MPVView/MpvProperties, dan mengembalikan pemulih keadaan semula.
 */
internal class MPVActivityDialogPause(
    private val playerProvider: () -> MPVView,
    private val audioPresence: MPVActivityAudioPresence,
) {
    /** Nilai pengaturan no_ui_pause: "always", "audio-only", atau "never"; diisi layar saat membaca pengaturan. */
    var mode = ""

    fun pause(): StateRestoreCallback {
        val useKeepOpen = when (mode) {
            "always" -> true
            "audio-only" -> audioPresence.isAudioOnly()
            else -> false // "never"
        }
        if (useKeepOpen) {
            // don't pause but set keep-open so mpv doesn't exit while the user is doing stuff
            Diag.behavior("player", "dialog-pause", "keep-open", mapOf("mode" to mode))
            val oldValue = MpvProperties.getString("keep-open")
            MpvProperties.setBoolean("keep-open", true)
            return {
                oldValue?.also { MpvProperties.setString("keep-open", it) }
            }
        }

        // Pause playback during UI dialogs
        Diag.behavior("player", "dialog-pause", "pause", mapOf("mode" to mode))
        val player = playerProvider()
        val wasPlayerPaused = player.paused ?: true
        player.paused = true
        return {
            if (!wasPlayerPaused)
                player.paused = false
        }
    }
}
