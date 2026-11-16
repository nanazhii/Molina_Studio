package com.molina.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.materialswitch.MaterialSwitch
import com.molina.core.security.DirectStreamPolicy
import com.molina.core.security.ShieldRegistry
import com.molina.core.security.ShieldSnapshot
import kotlinx.coroutines.launch

/**
 * Layar Keamanan: sakelar Molina Shield, ringkasan langsung dari snapshot, dan blokir terbaru
 * (hanya host dan kode alasan, tidak pernah URL atau token). Perubahan langsung disimpan
 * ([ShieldPrefs]) dan diterapkan ke Shield; Cinema memakainya pada pemutar yang dibuka berikutnya.
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

        val sum = TextView(ctx).apply { textSize = 14f; setPadding(0, pad / 2, 0, pad / 2) }
        val rec = TextView(ctx).apply { textSize = 12f }
        summary = sum
        recent = rec

        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad)
            addView(TextView(ctx).apply { text = "Keamanan"; textSize = 24f })
            addView(sum)
            addView(toggle("Molina Shield", cfg.enabled) { v -> ShieldPrefs.update(ctx) { it.copy(enabled = v) } })
            addView(note("Menyaring http/https pemutar lewat proxy lokal. Bila dimatikan, lalu lintas langsung tanpa penyaringan. Berlaku di pemutar yang dibuka berikutnya."))
            addView(toggle("Blokir pelacak", cfg.blockTrackers) { v -> ShieldPrefs.update(ctx) { it.copy(blockTrackers = v) } })
            addView(toggle("Izinkan jaringan lokal (NAS, DLNA)", cfg.allowLan) { v -> ShieldPrefs.update(ctx) { it.copy(allowLan = v) } })
            addView(note("Bawaan mati: jaringan dianggap tidak tepercaya. Nyalakan hanya bila memutar dari server di rumah."))
            addView(toggle("Hapus header Referer", cfg.stripReferer) { v -> ShieldPrefs.update(ctx) { it.copy(stripReferer = v) } })
            addView(TextView(ctx).apply { text = "Arus langsung rtmp/rtsp"; textSize = 14f; setPadding(0, pad / 2, 0, 0) })
            addView(RadioGroup(ctx).apply {
                listOf(
                    DirectStreamPolicy.PREFLIGHT to "Periksa dulu (port, pelacak, semua IP)",
                    DirectStreamPolicy.BLOCK to "Tolak semua rtmp/rtsp",
                ).forEach { (value, label) ->
                    addView(RadioButton(ctx).apply {
                        text = label
                        isChecked = value == cfg.directStreams
                        setOnCheckedChangeListener { _, on -> if (on) ShieldPrefs.update(ctx) { it.copy(directStreams = value) } }
                    })
                }
            })
            addView(note("Preflight tidak menutup DNS rebinding cepat; pilih tolak bila perlu."))
            addView(TextView(ctx).apply { text = "Diblokir terbaru"; textSize = 18f; setPadding(0, pad, 0, 0) })
            addView(rec)
        }
        return ScrollView(ctx).apply { addView(body) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val shield = ShieldRegistry.current()
        if (shield == null) {
            summary?.text = "Shield tidak tersedia di proses ini."
            recent?.text = "-"
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                shield.snapshot.collect { snap ->
                    summary?.text = ShieldSummary.text(snap, shield.failClosed())
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
