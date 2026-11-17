package com.molina.cinema

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.molina.core.ui.molinaThemed
import java.io.File

/**
 * Beranda Molina Cinema: pintu masuk ke pemutar mpv (MPVActivity, layar penuh). Memuat lencana mode
 * "Otomatis / Hanya audio", tombol Pilih berkas (penelusur folder), kolom path/tautan, tombol Putar,
 * dan daftar "Terakhir diputar". Kontrol pemutaran ada di pemutar itu sendiri.
 */
class CinemaFragment : Fragment() {
    private var status: TextView? = null
    private var badge: MaterialButton? = null
    private var recentBox: LinearLayout? = null
    private var accent: Int = 0
    private var audioOnly = false

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun prefs() = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun badgeText() = if (audioOnly) "Mode: Hanya audio" else "Mode: Otomatis"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext().molinaThemed(com.molina.core.ui.R.style.Theme_Molina_Cinema)
        val colBg = ContextCompat.getColor(ctx, com.molina.core.ui.R.color.molina_cinema_bg)
        val colPanel = ContextCompat.getColor(ctx, com.molina.core.ui.R.color.molina_cinema_panel)
        val colAccent = ContextCompat.getColor(ctx, com.molina.core.ui.R.color.molina_cinema_accent)
        accent = colAccent
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
            text = "Pilih berkas, atau masukkan path/tautan langsung, lalu tekan Putar."
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
        val pick = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Pilih berkas"
            isAllCaps = false
            textSize = 13f
            strokeColor = tint
            setOnClickListener { showPicker() }
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
            setOnClickListener { launch(input.text.toString()) }
        }
        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(bd, LinearLayout.LayoutParams(wrap, wrap).apply { marginEnd = dp(8) })
            addView(pick, LinearLayout.LayoutParams(wrap, wrap))
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
            addView(topRow, LinearLayout.LayoutParams(wrap, wrap))
            addView(row, LinearLayout.LayoutParams(matchW, wrap))
        }
        val rb = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        status = st
        badge = bd
        recentBox = rb
        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(st)
            addView(panel, LinearLayout.LayoutParams(matchW, wrap))
            addView(rb, LinearLayout.LayoutParams(matchW, wrap))
        }
        renderRecent()
        return ScrollView(ctx).apply {
            setBackgroundColor(colBg)
            isFillViewport = true
            addView(column, ViewGroup.LayoutParams(matchW, wrap))
        }
    }

    override fun onResume() {
        super.onResume()
        renderRecent() // kembali dari pemutar: urutan "Terakhir diputar" bisa berubah
    }

    /** Satu pintu peluncuran untuk tombol Putar, pemilih berkas, dan daftar terakhir diputar. */
    private fun launch(input: String) {
        when (val r = PlayerLauncher.open(requireContext(), input, audioOnly)) {
            is LaunchPlan.Empty -> status?.text = "Isi path atau tautan dulu."
            is LaunchPlan.Blocked -> status?.text = "Ditolak: ${r.message}"
            is LaunchPlan.Open -> {
                status?.text = "Membuka pemutar..."
                if (r.uri.startsWith("/")) rememberRecent(r.uri) // tautan tidak disimpan (bisa memuat token)
            }
        }
    }

    private fun readRecent(): List<String> = CinemaRecent.decode(prefs().getString(KEY_RECENT, null))

    private fun rememberRecent(path: String) {
        val next = CinemaRecent.push(readRecent(), path)
        prefs().edit().putString(KEY_RECENT, CinemaRecent.encode(next)).apply()
    }

    private fun renderRecent() {
        val box = recentBox ?: return
        box.removeAllViews()
        val items = readRecent().filter { File(it).exists() }
        if (items.isEmpty()) return
        val ctx = box.context
        val ripple = TypedValue().also { ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true) }
        box.addView(TextView(ctx).apply {
            text = "Terakhir diputar"
            textSize = 16f
            setTextColor(accent)
            setPadding(dp(16), dp(20), dp(16), dp(8))
        })
        for (p in items) {
            box.addView(TextView(ctx).apply {
                text = File(p).name
                textSize = 15f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(16), dp(14), dp(16), dp(14))
                setBackgroundResource(ripple.resourceId)
                isClickable = true
                setOnClickListener { launch(p) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    /** Penelusur folder sederhana: ketuk folder untuk masuk, ketuk berkas media untuk memutar. */
    private fun showPicker() {
        val c = requireContext()
        val root = Environment.getExternalStorageDirectory()
        var dir = CinemaBrowse.startDir(root)
        var shown: List<BrowseEntry> = emptyList()
        var parent: File? = null
        val adapter = ArrayAdapter<String>(c, android.R.layout.simple_list_item_1)
        val list = ListView(c).also { it.adapter = adapter }
        val dialog = MaterialAlertDialogBuilder(c).setView(list).setNegativeButton("Tutup", null).create()

        fun refresh() {
            parent = CinemaBrowse.parentWithin(dir, root)
            shown = CinemaBrowse.list(dir)
            adapter.clear()
            if (parent != null) adapter.add("..")
            for (e in shown) adapter.add(if (e.isDir) e.name + "/" else e.name)
            val rel = dir.path.removePrefix(root.path).ifEmpty { "/" }
            dialog.setTitle(if (shown.isEmpty()) "$rel (kosong)" else rel)
        }

        list.setOnItemClickListener { _, _, pos, _ ->
            val up = parent
            if (up != null && pos == 0) {
                dir = up
                refresh()
                return@setOnItemClickListener
            }
            val e = shown.getOrNull(pos - (if (up != null) 1 else 0)) ?: return@setOnItemClickListener
            if (e.isDir) {
                dir = e.file
                refresh()
            } else {
                dialog.dismiss()
                launch(e.file.path)
            }
        }
        refresh()
        dialog.show()
    }

    override fun onDestroyView() {
        status = null
        badge = null
        recentBox = null
        super.onDestroyView()
    }

    private companion object {
        const val PREFS = "molina_cinema"
        const val KEY_AUDIO_ONLY = "audio_only"
        const val KEY_RECENT = "recent"
    }
}
