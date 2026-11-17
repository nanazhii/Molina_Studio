package com.molina.cinema

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.molina.core.ui.molinaThemed

/**
 * Beranda Molina Cinema: pintu masuk ke pemutar mpv (MPVActivity, layar penuh). Memuat kolom path/tautan,
 * tombol Putar, dan lencana mode "Otomatis / Hanya audio". Kontrol pemutaran ada di pemutar itu sendiri.
 */
class CinemaFragment : Fragment() {
    private var status: TextView? = null
    private var badge: MaterialButton? = null
    private var audioOnly = false

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun prefs() = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun badgeText() = if (audioOnly) "Mode: Hanya audio" else "Mode: Otomatis"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext().molinaThemed(com.molina.core.ui.R.style.Theme_Molina_Cinema)
        val colBg = ContextCompat.getColor(ctx, com.molina.core.ui.R.color.molina_cinema_bg)
        val colPanel = ContextCompat.getColor(ctx, com.molina.core.ui.R.color.molina_cinema_panel)
        val colAccent = ContextCompat.getColor(ctx, com.molina.core.ui.R.color.molina_cinema_accent)
        val tint = android.content.res.ColorStateList.valueOf(colAccent)
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val matchW = ViewGroup.LayoutParams.MATCH_PARENT
        audioOnly = prefs().getBoolean(KEY_AUDIO_ONLY, false)

        val title = TextView(ctx).apply {
            text = "Molina Cinema"
            textSize = 22f
            setTextColor(colAccent)
            setPadding(dp(16), dp(24), dp(16), dp(4))
        }
        val st = TextView(ctx).apply {
            text = "Masukkan path berkas atau tautan langsung, lalu tekan Putar."
            setPadding(dp(16), dp(4), dp(16), dp(12))
        }
        val bd = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = badgeText()
            isAllCaps = false
            textSize = 13f
            strokeColor = tint
            setOnClickListener {
                audioOnly = !audioOnly
                prefs().edit().putBoolean(KEY_AUDIO_ONLY, audioOnly).apply()
                text = badgeText()
            }
        }
        val input = EditText(ctx).apply {
            hint = "Path atau tautan langsung ke berkas"
            textSize = 14f
            backgroundTintList = tint
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val play = MaterialButton(ctx).apply {
            text = "Putar"
            isAllCaps = false
            setOnClickListener {
                when (val r = PlayerLauncher.open(requireContext(), input.text.toString(), audioOnly)) {
                    is LaunchPlan.Empty -> status?.text = "Isi path atau tautan dulu."
                    is LaunchPlan.Blocked -> status?.text = "Ditolak: ${r.message}"
                    is LaunchPlan.Open -> status?.text = "Membuka pemutar..."
                }
            }
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(input, LinearLayout.LayoutParams(0, wrap, 1f))
            addView(play)
        }
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colPanel)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(bd, LinearLayout.LayoutParams(wrap, wrap))
            addView(row, LinearLayout.LayoutParams(matchW, wrap))
        }
        status = st
        badge = bd
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colBg)
            addView(title)
            addView(st)
            addView(panel, LinearLayout.LayoutParams(matchW, wrap))
        }
    }

    override fun onDestroyView() {
        status = null
        badge = null
        super.onDestroyView()
    }

    private companion object {
        const val PREFS = "molina_cinema"
        const val KEY_AUDIO_ONLY = "audio_only"
    }
}
