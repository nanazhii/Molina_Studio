package com.molina.player.stream

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import com.molina.player.databinding.ViewLoadingCardBinding

/**
 * Kartu kecil di tengah layar pemutar saat tautan sedang dicari, di-resolve, atau dibuka mpv: indikator, teks, dan chip
 * mesin (Langsung, NewPipe, yt-dlp, Web). Hanya tampilan: kapan tampil, berganti, atau hilang diputuskan
 * [StreamPlaybackCoordinator]. Sebagai jaring pengaman, kartu hilang sendiri bila tidak pernah ditutup.
 */
internal class LoadingCard(parent: RelativeLayout) {
    private val binding = ViewLoadingCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    private val autoHide = Runnable { hide() }

    init {
        binding.root.visibility = View.GONE
        val lp = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.addRule(RelativeLayout.CENTER_IN_PARENT)
        parent.addView(binding.root, lp)
    }

    /** [source] null = chip disembunyikan (mesin belum diketahui). */
    fun show(message: String, source: String?) {
        binding.label.text = message
        setSource(source)
        binding.root.removeCallbacks(autoHide)
        binding.root.visibility = View.VISIBLE
        binding.root.postDelayed(autoHide, SAFETY_MS)
    }

    fun setSource(source: String?) {
        binding.chip.text = source.orEmpty()
        binding.chip.visibility = if (source == null) View.GONE else View.VISIBLE
    }

    fun hide() {
        binding.root.removeCallbacks(autoHide)
        binding.root.visibility = View.GONE
    }

    private companion object {
        const val SAFETY_MS = 30_000L
    }
}
