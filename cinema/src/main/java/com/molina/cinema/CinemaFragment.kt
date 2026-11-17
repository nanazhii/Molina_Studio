// molina-patch-c1
package com.molina.cinema

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.molina.core.security.SHIELD_BLOCKED_CODE
import com.molina.core.ui.molinaThemed
import kotlinx.coroutines.launch

/**
 * Kontrol dasar Cinema (Patch C1): seek, ketuk ganda kiri/kanan +-10 dtk, kecepatan, trek audio/subtitle,
 * rasio layar, tunda audio/subtitle. UI hanya memakai MolinaPlayerSession (tanpa MPVLib).
 * Pemilih berkas menyusul lewat modul Berkas; sementara path/URL diketik manual.
 */
class CinemaFragment : Fragment(), SurfaceHolder.Callback {
    private var playback: MpvPlayback? = null
    private var themed: Context? = null
    private var status: TextView? = null
    private var toggle: MaterialButton? = null
    private var speedBtn: MaterialButton? = null
    private var aspectBtn: MaterialButton? = null
    private var seek: MarkedSeekBar? = null
    private var timeNow: TextView? = null
    private var timeTotal: TextView? = null
    private var seeking = false

    private var state = PlayerState.IDLE
    private var positionMs = 0L
    private var durationMs = 0L
    private var note = ""
    private var audioTracks: List<TrackInfo> = emptyList()
    private var subTracks: List<TrackInfo> = emptyList()
    private var speed = 1.0
    private var aspect = AspectMode.ORIGINAL
    private var audioDelayMs = 0L
    private var subDelayMs = 0L

    private val session: MolinaPlayerSession? get() = playback?.session

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun button(ctx: Context, label: String, onClick: () -> Unit): MaterialButton =
        MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext().molinaThemed(com.molina.core.ui.R.style.Theme_Molina_Cinema)
        themed = ctx
        val colBg = ContextCompat.getColor(ctx, com.molina.core.ui.R.color.molina_cinema_bg)
        val colPanel = ContextCompat.getColor(ctx, com.molina.core.ui.R.color.molina_cinema_panel)
        val colAccent = ContextCompat.getColor(ctx, com.molina.core.ui.R.color.molina_cinema_accent)
        val matchW = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(colBg) }
        val st = TextView(ctx).apply { setPadding(dp(16), dp(10), dp(16), dp(10)); setTextColor(colAccent); text = "Molina Cinema" }

        val surface = SurfaceView(ctx).also { it.holder.addCallback(this) }
        val detector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val s = session ?: return true
                s.seekRelative(if (e.x < surface.width / 2f) -SEEK_STEP_MS else SEEK_STEP_MS)
                return true
            }
        })
        surface.setOnTouchListener { _, ev -> detector.onTouchEvent(ev); true }

        val now = TextView(ctx).apply { text = CinemaFormat.time(0) }
        val total = TextView(ctx).apply { text = CinemaFormat.time(0) }
        val bar = MarkedSeekBar(ctx).apply {
            max = 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) now.text = CinemaFormat.time(p.toLong())
                }
                override fun onStartTrackingTouch(sb: SeekBar?) { seeking = true }
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    seeking = false
                    sb?.let { session?.seekTo(it.progress.toLong()) }
                }
            })
        }
        val seekRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(now)
            addView(bar, LinearLayout.LayoutParams(0, wrap, 1f).apply { setMargins(dp(8), 0, dp(8), 0) })
            addView(total)
        }

        val tg = button(ctx, "Putar") {
            val s = session ?: return@button
            if (state == PlayerState.PLAYING) s.pause() else s.play()
        }
        val transport = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button(ctx, "-10 dtk") { session?.seekRelative(-SEEK_STEP_MS) }, LinearLayout.LayoutParams(0, wrap, 1f))
            addView(tg, LinearLayout.LayoutParams(0, wrap, 1f))
            addView(button(ctx, "+10 dtk") { session?.seekRelative(SEEK_STEP_MS) }, LinearLayout.LayoutParams(0, wrap, 1f))
        }

        val spd = button(ctx, CinemaFormat.speed(speed)) {
            CinemaDialogs.speed(ctx, speed) { session?.setSpeed(it) }
        }
        val asp = button(ctx, CinemaFormat.aspectLabel(aspect)) {
            aspect = CinemaFormat.nextAspect(aspect)
            session?.setAspect(aspect)
            render()
        }
        val options = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(spd)
            addView(button(ctx, "Audio") {
                CinemaDialogs.tracks(ctx, "Trek audio", audioTracks, false) { id -> id?.let { session?.selectAudio(it) } }
            })
            addView(button(ctx, "Subtitle") {
                CinemaDialogs.tracks(ctx, "Subtitle", subTracks, true) { id -> session?.selectSubtitle(id) }
            })
            addView(asp)
            addView(button(ctx, "Tunda audio") {
                CinemaDialogs.delay(ctx, "Tunda audio", audioDelayMs) { audioDelayMs = it; session?.setAudioDelay(it) }
            })
            addView(button(ctx, "Tunda sub") {
                CinemaDialogs.delay(ctx, "Tunda subtitle", subDelayMs) { subDelayMs = it; session?.setSubtitleDelay(it) }
            })
        }
        val optionsScroll = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(options) }

        val input = EditText(ctx).apply {
            hint = "Path atau URL, mis. /storage/emulated/0/Molina/video.mp4"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val play = button(ctx, "Buka") {
            val uri = input.text.toString().trim()
            val s = session ?: return@button
            if (uri.isNotEmpty()) { note = ""; s.load(uri); s.play() }
        }
        val loadRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(input, LinearLayout.LayoutParams(0, wrap, 1f))
            addView(play)
        }

        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colPanel)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            addView(seekRow)
            addView(transport)
            addView(optionsScroll)
            addView(loadRow)
        }

        root.addView(st)
        root.addView(surface, LinearLayout.LayoutParams(matchW, 0, 1f))
        root.addView(panel, LinearLayout.LayoutParams(matchW, wrap))
        status = st
        toggle = tg
        speedBtn = spd
        aspectBtn = asp
        seek = bar
        timeNow = now
        timeTotal = total
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val pb = MpvPlayerFactory.open(requireContext().applicationContext)
        if (pb == null) {
            status?.text = "mpv tidak tersedia. Lihat Log Viewer (mpv.native-missing / mpv.init-failed)."
            return
        }
        playback = pb
        render()
        viewLifecycleOwner.lifecycleScope.launch {
            pb.session.events.collect { ev ->
                when (ev) {
                    is PlayerEvent.StateChanged -> state = ev.state
                    is PlayerEvent.Position -> { positionMs = ev.positionMs; durationMs = ev.durationMs }
                    is PlayerEvent.Error -> note = if (ev.code == SHIELD_BLOCKED_CODE) "Diblokir Shield: ${ev.message}" else "Gagal memuat. Lihat Log Viewer (chip BUG)."
                    is PlayerEvent.Loaded -> { note = ""; audioDelayMs = 0L; subDelayMs = 0L; audioTracks = emptyList(); subTracks = emptyList() }
                    PlayerEvent.Ended -> Unit
                    is PlayerEvent.TracksChanged -> { audioTracks = ev.audio; subTracks = ev.subtitles }
                    is PlayerEvent.SpeedChanged -> speed = ev.speed
                }
                render()
            }
        }
    }

    override fun onDestroyView() {
        playback?.session?.release()
        playback = null
        themed = null
        status = null
        toggle = null
        speedBtn = null
        aspectBtn = null
        seek = null
        timeNow = null
        timeTotal = null
        super.onDestroyView()
    }

    private fun render() {
        val id = playback?.sessionId ?: "-"
        status?.text = "Cinema $id | $state" + if (note.isEmpty()) "" else "\n$note"
        toggle?.text = if (state == PlayerState.PLAYING) "Jeda" else if (state == PlayerState.PAUSED) "Lanjut" else "Putar"
        speedBtn?.text = CinemaFormat.speed(speed)
        aspectBtn?.text = CinemaFormat.aspectLabel(aspect)
        timeTotal?.text = CinemaFormat.time(durationMs)
        if (!seeking) {
            seek?.apply {
                val maxMs = durationMs.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
                if (max != maxMs) max = maxMs
                progress = positionMs.coerceIn(0L, maxMs.toLong()).toInt()
            }
            timeNow?.text = CinemaFormat.time(positionMs)
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) { playback?.surface?.attach(holder.surface) }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        playback?.surface?.resize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) { playback?.surface?.detach() }

    private companion object {
        const val SEEK_STEP_MS = 10_000L
    }
}
