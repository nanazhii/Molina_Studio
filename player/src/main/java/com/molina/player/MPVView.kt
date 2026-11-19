package com.molina.player

import android.content.Context
import android.os.Environment
import android.preference.PreferenceManager
import android.util.AttributeSet
import android.util.Log
import android.view.*
import androidx.core.content.ContextCompat
import com.molina.player.MPVLib.MpvFormat.MPV_FORMAT_DOUBLE
import com.molina.player.MPVLib.MpvFormat.MPV_FORMAT_FLAG
import com.molina.player.MPVLib.MpvFormat.MPV_FORMAT_INT64
import com.molina.player.MPVLib.MpvFormat.MPV_FORMAT_NONE
import com.molina.player.MPVLib.MpvFormat.MPV_FORMAT_STRING
import com.molina.player.mpv.MpvGateway
import com.molina.player.mpv.MpvProperties
import kotlin.reflect.KProperty

internal class MPVView(context: Context, attrs: AttributeSet) : BaseMPVView(context, attrs) {
    override fun initOptions() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        // apply phone-optimized defaults
        MpvProperties.setOption("profile", "fast")
        MpvProperties.setOption("ytdl", "no") // molina: bundle yt-dlp dihapus; resolver URL (UrlResolver) menyusul

        // vo
        setVo(if (sharedPreferences.getBoolean("gpu_next", false))
            "gpu-next"
        else
            "gpu")

        // hwdec
        val hwdec = if (sharedPreferences.getBoolean("hardware_decoding", true))
            HWDECS
        else
            "no"

        // vo: set display fps as reported by android
        val disp = ContextCompat.getDisplayOrDefault(context)
        val refreshRate = disp.mode.refreshRate

        Log.v(TAG, "Display ${disp.displayId} reports FPS of $refreshRate")
        MpvProperties.setOption("display-fps-override", refreshRate.toString())

        // set non-complex options
        data class Property(val preferenceName: String, val mpvOption: String)
        val opts = arrayOf(
                Property("default_audio_language", "alang"),
                Property("default_subtitle_language", "slang"),

                // vo-related
                Property("video_scale", "scale"),
                Property("video_scale_param1", "scale-param1"),
                Property("video_scale_param2", "scale-param2"),

                Property("video_downscale", "dscale"),
                Property("video_downscale_param1", "dscale-param1"),
                Property("video_downscale_param2", "dscale-param2"),

                Property("video_tscale", "tscale"),
                Property("video_tscale_param1", "tscale-param1"),
                Property("video_tscale_param2", "tscale-param2")
        )

        for ((preferenceName, mpvOption) in opts) {
            val preference = sharedPreferences.getString(preferenceName, "")
            if (!preference.isNullOrBlank())
                MpvProperties.setOption(mpvOption, preference)
        }

        val debandMode = sharedPreferences.getString("video_debanding", "")
        if (debandMode == "gradfun") {
            // lower the default radius (16) to improve performance
            MpvProperties.setOption("vf", "gradfun=radius=12")
        } else if (debandMode == "gpu") {
            MpvProperties.setOption("deband", "yes")
        }

        val vidsync = sharedPreferences.getString("video_sync", resources.getString(R.string.pref_video_interpolation_sync_default))
        MpvProperties.setOption("video-sync", vidsync!!)

        if (sharedPreferences.getBoolean("video_interpolation", false))
            MpvProperties.setOption("interpolation", "yes")

        if (sharedPreferences.getBoolean("gpudebug", false))
            MpvProperties.setOption("gpu-debug", "yes")

        if (sharedPreferences.getBoolean("video_fastdecode", false)) {
            MpvProperties.setOption("vd-lavc-fast", "yes")
            MpvProperties.setOption("vd-lavc-skiploopfilter", "nonkey")
        }

        MpvProperties.setOption("gpu-context", "android")
        MpvProperties.setOption("opengl-es", "yes")
        MpvProperties.setOption("hwdec", hwdec)
        MpvProperties.setOption("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        MpvProperties.setOption("ao", "audiotrack,opensles")
        MpvProperties.setOption("audio-set-media-role", "yes")
        MpvProperties.setOption("tls-verify", "yes")
        MpvProperties.setOption("tls-ca-file", "${this.context.filesDir.path}/cacert.pem")
        MpvProperties.setOption("input-default-bindings", "yes")
        MpvProperties.setOption("flatten-editions", "yes")
        // molina: cache asimetris. Buffer maju cukup untuk 1-2 menit stream 720p; buffer mundur kecil
        // karena hanya dipakai seek ke belakang. Menghemat RAM native di perangkat 2-3 GB.
        val forwardMegs = 32
        val backMegs = 8
        MpvProperties.setOption("demuxer-max-bytes", "${forwardMegs * 1024 * 1024}")
        MpvProperties.setOption("demuxer-max-back-bytes", "${backMegs * 1024 * 1024}")
        MpvProperties.setOption("cache-secs", "90")
        MpvProperties.setOption("network-timeout", "20")
        //
        val screenshotDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        screenshotDir.mkdirs()
        MpvProperties.setOption("screenshot-directory", screenshotDir.path)
    }

    override fun postInitOptions() {
        // we need to call write-watch-later manually
        MpvProperties.setOption("save-position-on-quit", "no")
    }

    fun onPointerEvent(event: MotionEvent): Boolean {
        assert (event.isFromSource(InputDevice.SOURCE_CLASS_POINTER))
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            val h = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
            val v = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (h > 0)
                MpvGateway.command(arrayOf("keypress", "WHEEL_RIGHT", "$h"))
            else if (h < 0)
                MpvGateway.command(arrayOf("keypress", "WHEEL_LEFT", "${-h}"))
            if (v > 0)
                MpvGateway.command(arrayOf("keypress", "WHEEL_UP", "$v"))
            else if (v < 0)
                MpvGateway.command(arrayOf("keypress", "WHEEL_DOWN", "${-v}"))
            return true
        }
        return false
    }

    fun onKey(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_MULTIPLE)
            return false
        if (KeyEvent.isModifierKey(event.keyCode))
            return false

        var mapped = keyMapping[event.keyCode]
        if (mapped == null) {
            // Fallback to produced glyph
            if (!event.isPrintingKey) {
                if (event.repeatCount == 0)
                    Log.d(TAG, "Unmapped non-printable key ${event.keyCode}")
                return false
            }

            val ch = event.unicodeChar
            if (ch.and(KeyCharacterMap.COMBINING_ACCENT) != 0)
                return false // dead key
            mapped = ch.toChar().toString()
        }

        if (event.repeatCount > 0)
            return true // eat event but ignore it, mpv has its own key repeat

        val mod: MutableList<String> = mutableListOf()
        event.isShiftPressed && mod.add("shift")
        event.isCtrlPressed && mod.add("ctrl")
        event.isAltPressed && mod.add("alt")
        event.isMetaPressed && mod.add("meta")

        val action = if (event.action == KeyEvent.ACTION_DOWN) "keydown" else "keyup"
        mod.add(mapped)
        MpvGateway.command(arrayOf(action, mod.joinToString("+")))

        return true
    }

    override fun observeProperties() {
        // This observes all properties needed by MPVView, MPVActivity or other classes
        data class Property(val name: String, val format: Int = MPV_FORMAT_NONE)
        val p = arrayOf(
            Property("time-pos", MPV_FORMAT_INT64),
            Property("duration/full", MPV_FORMAT_DOUBLE),
            Property("pause", MPV_FORMAT_FLAG),
            Property("paused-for-cache", MPV_FORMAT_FLAG),
            Property("speed", MPV_FORMAT_STRING),
            Property("track-list"),
            Property("video-params/aspect", MPV_FORMAT_DOUBLE),
            Property("video-params/rotate", MPV_FORMAT_DOUBLE),
            Property("playlist-pos", MPV_FORMAT_INT64),
            Property("playlist-count", MPV_FORMAT_INT64),
            Property("current-tracks/video/image"),
            Property("media-title", MPV_FORMAT_STRING),
            Property("metadata"),
            Property("loop-playlist"),
            Property("loop-file"),
            Property("shuffle", MPV_FORMAT_FLAG),
            Property("hwdec-current"),
            Property("mute", MPV_FORMAT_FLAG),
            Property("current-tracks/audio/selected")
        )

        for ((name, format) in p)
            MpvProperties.observe(name, format)
    }

    fun addObserver(o: MPVLib.EventObserver) {
        MPVLib.addObserver(o)
    }
    fun removeObserver(o: MPVLib.EventObserver) {
        MPVLib.removeObserver(o)
    }

    data class Track(val mpvId: Int, val name: String)
    var tracks = mapOf<String, MutableList<Track>>(
            "audio" to arrayListOf(),
            "video" to arrayListOf(),
            "sub" to arrayListOf())

    fun loadTracks() {
        for (list in tracks.values) {
            list.clear()
            // pseudo-track to allow disabling audio/subs
            list.add(Track(-1, context.getString(R.string.track_off)))
        }
        val count = MpvProperties.getInt("track-list/count")!!
        // Note that because events are async, properties might disappear at any moment
        // so use ?: continue instead of !!
        for (i in 0 until count) {
            val type = MpvProperties.getString("track-list/$i/type") ?: continue
            if (!tracks.containsKey(type)) {
                Log.w(TAG, "Got unknown track type: $type")
                continue
            }
            val mpvId = MpvProperties.getInt("track-list/$i/id") ?: continue
            val lang = MpvProperties.getString("track-list/$i/lang")
            val title = MpvProperties.getString("track-list/$i/title")

            val trackName = if (!lang.isNullOrEmpty() && !title.isNullOrEmpty())
                context.getString(R.string.ui_track_title_lang, mpvId, title, lang)
            else if (!lang.isNullOrEmpty() || !title.isNullOrEmpty())
                context.getString(R.string.ui_track_text, mpvId, (lang ?: "") + (title ?: ""))
            else
                context.getString(R.string.ui_track, mpvId)
            tracks.getValue(type).add(Track(
                    mpvId=mpvId,
                    name=trackName
            ))
        }
    }

    data class PlaylistItem(val index: Int, val filename: String, val title: String?)

    fun loadPlaylist(): MutableList<PlaylistItem> {
        val playlist = mutableListOf<PlaylistItem>()
        val count = MpvProperties.getInt("playlist-count")!!
        for (i in 0 until count) {
            val filename = MpvProperties.getString("playlist/$i/filename")!!
            val title = MpvProperties.getString("playlist/$i/title")
            playlist.add(PlaylistItem(index=i, filename=filename, title=title))
        }
        return playlist
    }

    data class Chapter(val index: Int, val title: String?, val time: Double)

    fun loadChapters(): MutableList<Chapter> {
        val chapters = mutableListOf<Chapter>()
        val count = MpvProperties.getInt("chapter-list/count")!!
        for (i in 0 until count) {
            val title = MpvProperties.getString("chapter-list/$i/title")
            val time = MpvProperties.getDouble("chapter-list/$i/time")!!
            chapters.add(Chapter(
                    index=i,
                    title=title,
                    time=time
            ))
        }
        return chapters
    }

    // Property getters/setters

    var paused: Boolean?
        get() = MpvProperties.getBoolean("pause")
        set(paused) = MpvProperties.setBoolean("pause", paused!!)

    var timePos: Double?
        get() = MpvProperties.getDouble("time-pos/full")
        set(progress) = MpvProperties.setDouble("time-pos", progress!!)

    /** name of currently active hardware decoder or "no" */
    val hwdecActive: String
        get() = MpvProperties.getString("hwdec-current") ?: "no"

    var playbackSpeed: Double?
        get() = MpvProperties.getDouble("speed")
        set(speed) = MpvProperties.setDouble("speed", speed!!)

    var subDelay: Double?
        get() = MpvProperties.getDouble("sub-delay")
        set(speed) = MpvProperties.setDouble("sub-delay", speed!!)

    var secondarySubDelay: Double?
        get() = MpvProperties.getDouble("secondary-sub-delay")
        set(speed) = MpvProperties.setDouble("secondary-sub-delay", speed!!)

    val estimatedVfFps: Double?
        get() = MpvProperties.getDouble("estimated-vf-fps")

    /**
     * Returns the video aspect ratio. Rotation is taken into account.
     */
    fun getVideoAspect(): Double? {
        return MpvProperties.getDouble("video-params/aspect")?.let {
            if (it < 0.001)
                return 0.0
            val rot = MpvProperties.getInt("video-params/rotate") ?: 0
            if (rot % 180 == 90)
                1.0 / it
            else
                it
        }
    }

    fun setAudioSessionId(id: Int) {
        MpvProperties.setInt("audiotrack-session-id", id)
        MpvProperties.setInt("aaudio-session-id", id)
    }

    class TrackDelegate(private val name: String) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
            val v = MpvProperties.getString(name)
            // we can get null here for "no" or other invalid value
            return v?.toIntOrNull() ?: -1
        }
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            if (value == -1)
                MpvProperties.setString(name, "no")
            else
                MpvProperties.setInt(name, value)
        }
    }

    var vid: Int by TrackDelegate("vid")
    var sid: Int by TrackDelegate("sid")
    var secondarySid: Int by TrackDelegate("secondary-sid")
    var aid: Int by TrackDelegate("aid")

    // Commands

    fun cyclePause() { MpvGateway.command(arrayOf("cycle", "pause")) }
    fun cycleAudio() { MpvGateway.command(arrayOf("cycle", "audio")) }
    fun cycleSub() { MpvGateway.command(arrayOf("cycle", "sub")) }
    fun cycleHwdec() { MpvGateway.command(arrayOf("cycle-values", "hwdec", HWDECS, "no")) }

    fun cycleSpeed() {
        val speeds = arrayOf(0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0)
        val currentSpeed = playbackSpeed ?: 1.0
        val index = speeds.indexOfFirst { it > currentSpeed }
        playbackSpeed = speeds[if (index == -1) 0 else index]
    }

    fun getRepeat(): Int {
        return when (MpvProperties.getString("loop-playlist") +
                MpvProperties.getString("loop-file")) {
            "noinf" -> 2
            "infno" -> 1
            else -> 0
        }
    }

    fun cycleRepeat() {
        when (val state = getRepeat()) {
            0, 1 -> {
                MpvProperties.setString("loop-playlist", if (state == 1) "no" else "inf")
                MpvProperties.setString("loop-file", if (state == 1) "inf" else "no")
            }
            2 -> MpvProperties.setString("loop-file", "no")
        }
    }

    fun getShuffle(): Boolean {
        return MpvProperties.getBoolean("shuffle") == true
    }

    fun changeShuffle(cycle: Boolean, value: Boolean = true) {
        // Use the 'shuffle' property to store the shuffled state, since changing
        // it at runtime doesn't do anything.
        val state = getShuffle()
        val newState = if (cycle) state.xor(value) else value
        if (state == newState)
            return
        MpvGateway.command(arrayOf(if (newState) "playlist-shuffle" else "playlist-unshuffle"))
        MpvProperties.setBoolean("shuffle", newState)
    }

    companion object {
        private const val TAG = "mpv"

        // mpv option `hwdec` is set to this
        private const val HWDECS = "mediacodec,mediacodec-copy"
    }
}
