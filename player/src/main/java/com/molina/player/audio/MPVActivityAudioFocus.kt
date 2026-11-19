package com.molina.player.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.molina.core.common.diag.Diag
import com.molina.player.MPVView
import com.molina.player.mpv.MpvGateway

/**
 * Fokus audio dan penerima "audio jadi berisik" (headset dicabut).
 * Tanggung jawab tunggal: meminta/melepas fokus, mendaftarkan penerima, dan menerjemahkan perubahan fokus menjadi
 * jeda/lanjut atau pengecilan volume mpv (lewat MPVView dan MpvGateway).
 *
 * [sync] dipanggil dari thread event mpv; callback fokus dan penerima datang di thread utama. Status pendaftaran dan
 * permintaan dijaga [lock]. Setelah [release] tidak ada pendaftaran penerima atau permintaan fokus baru, sehingga
 * event mpv yang terlambat tidak bisa memasang ulang penerima pada layar yang sudah ditutup.
 */
internal class MPVActivityAudioFocus(
    private val context: Context,
    private val audioManagerProvider: () -> AudioManager?,
    private val playerProvider: () -> MPVView,
    private val isFinishing: () -> Boolean,
) {
    /** Nilai pengaturan ignore_audio_focus; diisi layar saat membaca pengaturan. */
    @Volatile
    var ignore = false

    private val lock = Any()
    private var request: AudioFocusRequestCompat? = null
    private var restore: () -> Unit = {}
    private var noisyRegistered = false

    @Volatile
    private var released = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                onChange(AudioManager.AUDIOFOCUS_LOSS, "noisy")
        }
    }

    /**
     * Selaraskan fokus dengan status putar. [audible] true bila tidak sedang jeda dan audio benar-benar keluar:
     * penerima didaftarkan dan fokus diminta ulang (setiap pemutaran dilanjutkan, lihat diskusi #1066);
     * selain itu penerima dilepas. Fokus sendiri baru dilepas di [release].
     * @warning Panggil dari thread event, bukan thread UI.
     */
    fun sync(audible: Boolean) {
        val granted: Boolean
        synchronized(lock) {
            if (released)
                return
            if (!audible) {
                unregisterNoisy()
                return
            }
            registerNoisy()
            granted = requestFocus()
        }
        onChange(if (granted) AudioManager.AUDIOFOCUS_GAIN else AudioManager.AUDIOFOCUS_LOSS, "request")
    }

    /** Lepas penerima dan fokus saat layar ditutup. Aman dipanggil berulang. */
    fun release() {
        synchronized(lock) {
            if (released)
                return
            released = true
            unregisterNoisy()
            val req = request
            val manager = audioManagerProvider()
            if (req != null && manager != null)
                AudioManagerCompat.abandonAudioFocusRequest(manager, req)
            request = null
            restore = {}
        }
        Diag.behavior("player", "audio-focus-release", "fokus audio dan penerima dilepas")
    }

    // wajib dipanggil di dalam lock
    private fun registerNoisy() {
        if (noisyRegistered)
            return
        context.registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        noisyRegistered = true
    }

    // wajib dipanggil di dalam lock
    private fun unregisterNoisy() {
        if (!noisyRegistered)
            return
        context.unregisterReceiver(noisyReceiver)
        noisyRegistered = false
    }

    // wajib dipanggil di dalam lock
    private fun requestFocus(): Boolean {
        val manager = audioManagerProvider() ?: return false
        val req = request ?:
            with(AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)) {
                setAudioAttributes(with(AudioAttributesCompat.Builder()) {
                    // N.B.: libmpv may use different values in ao_audiotrack, but here we always pretend to be music.
                    setUsage(AudioAttributesCompat.USAGE_MEDIA)
                    setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                    build()
                })
                setOnAudioFocusChangeListener { onChange(it, "callback") }
                build()
            }
        val res = AudioManagerCompat.requestAudioFocus(manager, req)
        if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            request = req
            return true
        }
        Diag.behavior("player", "audio-focus-denied", "sistem menolak permintaan fokus audio", mapOf("result" to res.toString()))
        return false
    }

    // This handles both "real" audio focus changes by the callbacks, which aren't
    // really used anymore after Android 12 (except for AUDIOFOCUS_LOSS),
    // as well as actions equivalent to a focus change that we make up ourselves.
    private fun onChange(type: Int, source: String) {
        Diag.behavior("player", "audio-focus-change", typeName(type), mapOf("source" to source))
        if (ignore || released || isFinishing())
            return
        val player = playerProvider()
        when (type) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                val wasPlayerPaused = player.paused ?: false
                player.paused = true
                synchronized(lock) {
                    // loss can occur in addition to ducking, so remember the old callback
                    val old = restore
                    restore = {
                        old()
                        if (!wasPlayerPaused) playerProvider().paused = false
                    }
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                MpvGateway.command(arrayOf("multiply", "volume", DUCKING.toString()))
                synchronized(lock) {
                    restore = {
                        MpvGateway.command(arrayOf("multiply", "volume", (1f / DUCKING).toString()))
                    }
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                val pending = synchronized(lock) {
                    val r = restore
                    restore = {}
                    r
                }
                pending()
            }
        }
    }

    private fun typeName(type: Int) = when (type) {
        AudioManager.AUDIOFOCUS_GAIN -> "gain"
        AudioManager.AUDIOFOCUS_LOSS -> "loss"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "loss-transient"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "duck"
        else -> type.toString()
    }

    private companion object {
        // fraction to which audio volume is ducked on loss of audio focus
        const val DUCKING = 0.5f
    }
}
