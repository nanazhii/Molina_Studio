package com.molina.settings

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.switchmaterial.SwitchMaterial
import com.molina.log.LogcatOptions
import com.molina.log.LogcatReader
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Penampil logcat setara adb logcat (threadtime): seluruh buffer lalu aliran langsung. */
class LogViewerFragment : Fragment() {
    private val lines = ArrayDeque<String>()
    private var dirty = false
    private var onlyMolina = false
    private lateinit var output: TextView
    private lateinit var scroll: ScrollView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val pad = (12 * resources.displayMetrics.density).toInt()
        val banner = TextView(ctx).apply {
            text = if (LogcatReader.hasFullAccess(ctx)) {
                "Mode penuh: semua proses (setara adb logcat)"
            } else {
                "Mode terbatas: hanya log proses Molina. Beri izin READ_LOGS lewat adb untuk mode penuh."
            }
            setPadding(pad, pad * 3, pad, pad / 2)
        }
        val toggle = SwitchMaterial(ctx).apply {
            text = "Hanya tag Molina/"
            setPadding(pad, 0, pad, 0)
            setOnCheckedChangeListener { _, checked -> onlyMolina = checked; dirty = true }
        }
        output = TextView(ctx).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(pad, pad, pad, pad)
        }
        scroll = ScrollView(ctx).apply { addView(output) }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(banner)
            addView(toggle)
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                lines.clear()
                dirty = true
                val ticker = launch {
                    while (isActive) {
                        delay(250)
                        if (dirty) { dirty = false; render() }
                    }
                }
                try {
                    LogcatReader.stream(LogcatOptions()).collect { line ->
                        lines.addLast(line)
                        if (lines.size > MAX_LINES) lines.removeFirst()
                        dirty = true
                    }
                } catch (e: IOException) {
                    lines.addLast("[logcat gagal dijalankan: " + e.message + "]")
                    dirty = true
                } finally {
                    ticker.cancel()
                }
            }
        }
    }

    private fun render() {
        val shown = if (onlyMolina) lines.filter { it.contains(" Molina/") } else lines
        output.text = shown.joinToString("\n")
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private companion object {
        const val MAX_LINES = 1500
    }
}
