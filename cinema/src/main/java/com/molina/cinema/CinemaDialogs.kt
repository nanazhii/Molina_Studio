// molina-patch-c1
// Pengganti SubTrackDialog/SpeedPickerDialog/SubDelayDialog repo lama: tanpa viewBinding, hanya kontrak player-api.
package com.molina.cinema

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

internal object CinemaDialogs {
    private fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    /** [allowOff] menambah baris "Mati" (onPick(null)), dipakai untuk subtitle. */
    fun tracks(ctx: Context, title: String, tracks: List<TrackInfo>, allowOff: Boolean, onPick: (Int?) -> Unit) {
        if (tracks.isEmpty()) {
            MaterialAlertDialogBuilder(ctx).setTitle(title)
                .setMessage("Tidak ada trek pada berkas ini.")
                .setPositiveButton("Tutup", null).show()
            return
        }
        val labels = ArrayList<String>()
        val ids = ArrayList<Int?>()
        if (allowOff) { labels.add("Mati"); ids.add(null) }
        for (t in tracks) { labels.add(t.displayName); ids.add(t.id) }
        val sel = ids.indexOfFirst { id -> id != null && tracks.any { it.id == id && it.selected } }
        val checked = if (sel >= 0) sel else if (allowOff) 0 else -1
        MaterialAlertDialogBuilder(ctx).setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), checked) { d, which -> d.dismiss(); onPick(ids[which]) }
            .setNegativeButton("Batal", null).show()
    }

    fun speed(ctx: Context, current: Double, onApply: (Double) -> Unit) {
        val pad = dp(ctx, 20)
        val label = TextView(ctx).apply { textSize = 18f; gravity = Gravity.CENTER }
        val bar = SeekBar(ctx).apply { max = SpeedScale.MAX_PROGRESS; progress = SpeedScale.fromSpeed(current) }
        label.text = CinemaFormat.speed(SpeedScale.toSpeed(bar.progress))
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                label.text = CinemaFormat.speed(SpeedScale.toSpeed(p))
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
            addView(label)
            addView(bar)
        }
        MaterialAlertDialogBuilder(ctx).setTitle("Kecepatan").setView(body)
            .setPositiveButton("Terapkan") { _, _ -> onApply(SpeedScale.toSpeed(bar.progress)) }
            .setNeutralButton("1.00x") { _, _ -> onApply(1.0) }
            .setNegativeButton("Batal", null).show()
    }

    /** Tunda audio/subtitle: langkah -500/-100/+100/+500 ms, nilai dijepit PlayerLimits.clampDelayMs. */
    fun delay(ctx: Context, title: String, currentMs: Long, onApply: (Long) -> Unit) {
        val pad = dp(ctx, 20)
        var value = PlayerLimits.clampDelayMs(currentMs)
        val label = TextView(ctx).apply { textSize = 18f; gravity = Gravity.CENTER }
        fun refresh() { label.text = CinemaFormat.delay(value) }
        refresh()
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        for (d in longArrayOf(-500, -100, 100, 500)) {
            val b = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = CinemaFormat.delay(d)
                isAllCaps = false
                setOnClickListener { value = PlayerLimits.clampDelayMs(value + d); refresh() }
            }
            row.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
            addView(label)
            addView(row)
        }
        MaterialAlertDialogBuilder(ctx).setTitle(title).setView(body)
            .setPositiveButton("Terapkan") { _, _ -> onApply(value) }
            .setNeutralButton("Reset") { _, _ -> onApply(0L) }
            .setNegativeButton("Batal", null).show()
    }
}
