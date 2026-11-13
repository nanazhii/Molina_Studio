// molina-patch12
package com.molina.settings

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.molina.core.ui.R as UiR
import com.molina.log.LogcatOptions
import com.molina.log.LogcatReader
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Penampil logcat setara adb logcat (threadtime): seluruh buffer lalu aliran langsung.
 * Chip filter menyaring peristiwa [CRASH]/[BUG]/[BEHAV]; barisnya diwarnai menurut jenis.
 */
class LogViewerFragment : Fragment() {
    private val lines = ArrayDeque<String>()
    private val chips = LinkedHashMap<EventFilter, Chip>()
    private var dirty = false
    private var onlyMolina = false
    private var filter = EventFilter.ALL
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
        chips.clear()
        val group = ChipGroup(ctx).apply {
            isSingleLine = true
            isSingleSelection = true
            isSelectionRequired = true
            setPadding(pad, 0, pad, 0)
        }
        for (f in EventFilter.values()) {
            val chip = Chip(ctx).apply {
                text = f.label
                isCheckable = true
                isChecked = f == filter
                setOnClickListener { filter = f; dirty = true }
            }
            chips[f] = chip
            group.addView(chip)
        }
        val chipRow = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(group)
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
            addView(chipRow)
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

    override fun onDestroyView() {
        chips.clear()
        super.onDestroyView()
    }

    private fun render() {
        if (!isAdded) return
        val all = LogFilter.classify(lines)
        val counts = LogFilter.counts(all)
        for ((f, chip) in chips) {
            val n = counts[f] ?: 0
            chip.text = if (n > 0) f.label + " " + n else f.label
        }
        output.text = colorize(LogFilter.select(all, filter, onlyMolina))
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun colorize(shown: List<ViewLine>): CharSequence {
        val ctx = requireContext()
        val colors = mapOf(
            EventFilter.CRASH to ContextCompat.getColor(ctx, UiR.color.molina_error),
            EventFilter.BUG to ContextCompat.getColor(ctx, UiR.color.molina_cinema_accent),
            EventFilter.BEHAV to ContextCompat.getColor(ctx, UiR.color.molina_terminal_accent),
        )
        val sb = SpannableStringBuilder()
        for ((i, v) in shown.withIndex()) {
            if (i > 0) sb.append('\n')
            val start = sb.length
            sb.append(v.text)
            colors[v.kind]?.let {
                sb.setSpan(ForegroundColorSpan(it), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return sb
    }

    private companion object {
        const val MAX_LINES = 1500
    }
}
