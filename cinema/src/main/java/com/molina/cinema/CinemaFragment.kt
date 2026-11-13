// molina-patch13e
package com.molina.cinema

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Tahap uji pemutar (13e): bidang path/URL manual. Pemilih berkas menyusul lewat modul Berkas.
 * Sesi dibuat di onViewCreated dan dilepas di onDestroyView (pemutaran berhenti saat pindah tab).
 */
class CinemaFragment : Fragment(), SurfaceHolder.Callback {
    private var playback: MpvPlayback? = null
    private var status: TextView? = null
    private var toggle: MaterialButton? = null
    private var state = PlayerState.IDLE
    private var positionMs = 0L
    private var durationMs = 0L
    private var note = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val st = TextView(ctx).apply { setPadding(32, 24, 32, 8); text = "Molina Cinema" }
        val surface = SurfaceView(ctx).also { it.holder.addCallback(this) }
        val input = EditText(ctx).apply {
            hint = "Path atau URL, mis. /storage/emulated/0/Molina/video.mp4"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val play = MaterialButton(ctx).apply {
            text = "Putar"
            setOnClickListener {
                val uri = input.text.toString().trim()
                val s = playback?.session ?: return@setOnClickListener
                if (uri.isNotEmpty()) { note = ""; s.load(uri); s.play() }
            }
        }
        val tg = MaterialButton(ctx).apply {
            text = "Jeda"
            setOnClickListener {
                val s = playback?.session ?: return@setOnClickListener
                if (state == PlayerState.PLAYING) s.pause() else s.play()
            }
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(play, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(tg, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(st)
        root.addView(surface, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(input)
        root.addView(row)
        status = st
        toggle = tg
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
                    is PlayerEvent.Error -> note = "Gagal memuat. Lihat Log Viewer (chip BUG)."
                    is PlayerEvent.Loaded -> note = ""
                    PlayerEvent.Ended -> Unit
                }
                render()
            }
        }
    }

    override fun onDestroyView() {
        playback?.session?.release()
        playback = null
        status = null
        toggle = null
        super.onDestroyView()
    }

    private fun render() {
        val id = playback?.sessionId ?: "-"
        status?.text = "Cinema $id | $state | ${fmt(positionMs)} / ${fmt(durationMs)}" + if (note.isEmpty()) "" else "\n$note"
        toggle?.text = if (state == PlayerState.PLAYING) "Jeda" else "Lanjut"
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    override fun surfaceCreated(holder: SurfaceHolder) { playback?.surface?.attach(holder.surface) }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        playback?.surface?.resize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) { playback?.surface?.detach() }
}
