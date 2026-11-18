package com.molina.settings

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.materialswitch.MaterialSwitch
import com.molina.core.security.ShieldRegistry
import com.molina.core.ui.R as UiR
import kotlinx.coroutines.launch

/**
 * Layar Keamanan: sakelar Molina Shield, ringkasan langsung dari snapshot, dan blokir terbaru
 * (hanya host dan kode alasan, tidak pernah URL atau token). Perubahan langsung disimpan
 * ([ShieldPrefs]) dan diterapkan ke Shield; Terminal memakainya pada sesi yang dibuka berikutnya.
 */
class SecurityFragment : Fragment() {
    private var summary: TextView? = null
    private var recent: TextView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val pad = (16 * resources.displayMetrics.density).toInt()
        val cfg = ShieldPrefs.load(ctx)

        fun note(t: String) = TextView(ctx).apply { text = t; textSize = 12f }
        fun toggle(label: String, on: Boolean, apply: (Boolean) -> Unit) = MaterialSwitch(ctx).apply {
            text = label
            isChecked = on
            setOnCheckedChangeListener { _, v -> apply(v) }
        }

        val dens = resources.displayMetrics.density
        val sum = TextView(ctx).apply {
            textSize = 14f
            setPadding(pad, pad, pad, pad)
            // kartu: latar permukaan, bingkai tipis molina_header_frame (sama dengan daftar arsip)
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(ctx, UiR.color.molina_surface_variant))
                cornerRadius = 6 * dens
                setStroke(dens.toInt().coerceAtLeast(1), ContextCompat.getColor(ctx, UiR.color.molina_header_frame))
            }
        }
        val rec = TextView(ctx).apply { textSize = 12f }
        summary = sum
        recent = rec

        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad)
            addView(TextView(ctx).apply { text = "Keamanan"; textSize = 24f })
            addView(sum, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = pad / 2; bottomMargin = pad / 2 })
            addView(toggle("Molina Shield", cfg.enabled) { v -> ShieldPrefs.update(ctx) { it.copy(enabled = v) } })
            addView(note("Menyaring lalu lintas http/https lewat proxy lokal. Bila dimatikan, lalu lintas langsung tanpa penyaringan. Berlaku di sesi terminal yang dibuka berikutnya."))
            addView(toggle("Blokir pelacak", cfg.blockTrackers) { v -> ShieldPrefs.update(ctx) { it.copy(blockTrackers = v) } })
            addView(toggle("Izinkan jaringan lokal (NAS, DLNA)", cfg.allowLan) { v -> ShieldPrefs.update(ctx) { it.copy(allowLan = v) } })
            addView(note("Bawaan mati: jaringan dianggap tidak tepercaya. Nyalakan hanya bila perlu mengakses server di jaringan rumah."))
            addView(toggle("Hapus header Referer", cfg.stripReferer) { v -> ShieldPrefs.update(ctx) { it.copy(stripReferer = v) } })
            addView(TextView(ctx).apply { text = "Diblokir terbaru"; textSize = 18f; setPadding(0, pad, 0, 0) })
            addView(rec)
        }
        return ScrollView(ctx).apply { addView(body) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        val teal = ContextCompat.getColor(ctx, UiR.color.molina_terminal_accent)
        val amber = ContextCompat.getColor(ctx, UiR.color.molina_brand_accent)
        val shield = ShieldRegistry.current()
        if (shield == null) {
            summary?.text = "Shield tidak tersedia di proses ini."
            summary?.setTextColor(amber)
            recent?.text = "-"
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                shield.snapshot.collect { snap ->
                    val closed = shield.failClosed()
                    summary?.text = ShieldSummary.text(snap, closed)
                    summary?.setTextColor(if (ShieldSummary.level(snap, closed) == ShieldSummary.Level.ACTIVE) teal else amber)
                    val items = shield.recentBlocks().takeLast(10).reversed()
                    recent?.text = if (items.isEmpty()) "Belum ada." else items.joinToString("\n") { it.host + "  \u00b7  " + it.code }
                }
            }
        }
    }

    override fun onDestroyView() {
        summary = null
        recent = null
        super.onDestroyView()
    }
}
