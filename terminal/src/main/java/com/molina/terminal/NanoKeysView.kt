// molina-patch21
package com.molina.terminal

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.molina.core.ui.R as UiR
import com.molina.terminal.proot.NanoProtocol
import com.molina.terminal.proot.NanoProtocol.Mode

/**
 * Baris tombol nano berbentuk drop-up: gagang tipis "NANO <berkas>" di bawah, baris tombol ikon yang bisa digeser
 * ke samping tumbuh ke atas darinya. Hanya tampil saat judul sesi "nano: <berkas>" (dipasang pembungkus nano) atau
 * dialog "nano-run: ..." aktif. Tombol RUN dan FIX hanya untuk berkas skrip (py, sh, js, ...).
 * Prompt simpan/nama berkas/periksa-skrip dibaca dari layar dan mengganti ikon dengan tombol berlabel (wajib terlihat).
 * Semua dipanggil dari main thread.
 *
 * @param readRows baris layar emulator; [bottom] <= 0 berarti seluruh layar. Null bila tidak ada emulator.
 */
internal class NanoKeysView(
    context: Context,
    private val send: (String) -> Unit,
    private val title: () -> String?,
    private val readRows: (bottom: Int) -> List<String>?,
    private val isOpen: () -> Boolean,
    private val setOpen: (Boolean) -> Unit,
) : LinearLayout(context) {

    private class Key(val icon: Int, val tint: Int, val desc: Int, val seq: String, val scriptOnly: Boolean = false)

    private data class Snapshot(
        val mode: Mode,
        val name: String,
        val choices: List<NanoProtocol.Choice>?,
        val open: Boolean,
    )

    private val accent = color(UiR.color.molina_terminal_accent)
    private val green = color(UiR.color.molina_key_green)
    private val blue = color(UiR.color.molina_key_blue)
    private val red = color(UiR.color.molina_key_red)
    private val amber = color(R.color.molina_nano_fix)
    private val frame = color(R.color.molina_nano_frame)
    private val plain = color(UiR.color.molina_on_surface)
    private val black = color(UiR.color.molina_extrakey_bg)

    private val keyRow = LinearLayout(context).apply { orientation = HORIZONTAL }
    private val scroller = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        isFillViewport = false
        addView(keyRow)
    }
    private val nameView = TextView(context)
    private val arrowView = TextView(context)
    private val handle: LinearLayout
    private var last: Snapshot? = null
    private var forced = false

    private val poller = object : Runnable {
        override fun run() {
            refresh()
            postDelayed(this, POLL_MS)
        }
    }

    init {
        orientation = VERTICAL
        visibility = GONE
        setPadding(dp(6), dp(1), dp(6), dp(1))

        scroller.background = rounded(black, frame, 2, 12)
        scroller.setPadding(dp(3), dp(3), dp(3), dp(3))
        addView(scroller, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(2) })

        val tag = TextView(context).apply {
            text = "NANO"
            setTextColor(frame)
            textSize = 11f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        nameView.apply {
            setTextColor(ColorUtils.setAlphaComponent(plain, 0xB3))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        arrowView.apply {
            setTextColor(frame)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
        }
        handle = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(24)
            setPadding(dp(10), 0, dp(10), 0)
            background = rounded(black, frame, 1, 8)
            contentDescription = context.getString(R.string.terminal_nano_handle)
            isFocusable = false
            isFocusableInTouchMode = false
            setOnClickListener {
                if (forced) return@setOnClickListener
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                setOpen(!isOpen())
                refresh()
            }
            addView(tag)
            addView(nameView, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8); marginEnd = dp(8) })
            addView(arrowView)
        }
        addView(handle, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(poller)
        post(poller)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(poller)
        super.onDetachedFromWindow()
    }

    /** Baca judul sesi dan layar, lalu gambar ulang hanya bila ada yang berubah. */
    fun refresh() {
        val t = title()
        val runDialog = NanoProtocol.isRunDialogTitle(t)
        if (!NanoProtocol.isEditorTitle(t) && !runDialog) {
            hide()
            return
        }
        var mode = Mode.EDIT
        var choices: List<NanoProtocol.Choice>? = null
        if (runDialog) {
            choices = runCatching { readRows(0) }.getOrNull()?.let { NanoProtocol.parseRunChoices(it) }
            if (choices == null) { // skrip sedang jalan / dialog belum tampil: tanpa baris tombol
                hide()
                return
            }
            mode = Mode.RUNFIX
        } else {
            mode = runCatching { readRows(3) }.getOrNull()?.let { NanoProtocol.detectEditPrompt(it) } ?: Mode.EDIT
        }
        val next = Snapshot(mode, NanoProtocol.fileName(t), choices, isOpen())
        if (next == last && visibility == VISIBLE) return
        last = next
        render(next)
    }

    private fun hide() {
        if (visibility != GONE) visibility = GONE
        last = null
    }

    private fun render(s: Snapshot) {
        forced = s.mode != Mode.EDIT // prompt simpan/nama/periksa-skrip: tombol wajib terlihat
        val open = forced || s.open
        keyRow.removeAllViews()
        when (s.mode) {
            Mode.EDIT -> editKeys(NanoProtocol.isScript(s.name)).forEach { keyRow.addView(iconKey(it), cell(KEY_W)) }
            Mode.SAVE -> {
                keyRow.addView(labelKey(R.string.terminal_nano_yes, green, NanoProtocol.Seq.YES, false), cell(PROMPT_W))
                keyRow.addView(labelKey(R.string.terminal_nano_no, red, NanoProtocol.Seq.NO, false), cell(PROMPT_W))
                keyRow.addView(labelKey(R.string.terminal_nano_cancel, blue, NanoProtocol.Seq.CANCEL, false), cell(PROMPT_W))
            }
            Mode.NAME -> {
                keyRow.addView(labelKey(R.string.terminal_nano_enter, green, NanoProtocol.Seq.ENTER, false), cell(PROMPT_W))
                keyRow.addView(labelKey(R.string.terminal_nano_cancel, blue, NanoProtocol.Seq.CANCEL, false), cell(PROMPT_W))
            }
            Mode.RUNFIX -> s.choices.orEmpty().forEach { c ->
                keyRow.addView(labelText(c.label, runTint(c.key), NanoProtocol.sequenceOf(c), c.isDefault), cell(RUN_W))
            }
        }
        scroller.visibility = if (open) VISIBLE else GONE
        nameView.text = when (s.mode) {
            Mode.EDIT -> s.name
            Mode.SAVE -> context.getString(R.string.terminal_nano_title_save)
            Mode.NAME -> context.getString(R.string.terminal_nano_title_name)
            Mode.RUNFIX -> context.getString(R.string.terminal_nano_title_run)
        }
        val tint = if (forced) amber else frame
        (handle.background as? GradientDrawable)?.setStroke(dp(1), tint)
        (scroller.background as? GradientDrawable)?.setStroke(dp(2), if (forced) amber else frame)
        (handle.getChildAt(0) as? TextView)?.setTextColor(tint)
        arrowView.setTextColor(tint)
        arrowView.text = if (forced) "" else if (open) "\u25BE" else "\u25B4"
        visibility = VISIBLE
    }

    private fun editKeys(script: Boolean): List<Key> {
        val all = listOf(
            Key(R.drawable.ic_nano_save, blue, R.string.terminal_nano_save, NanoProtocol.Seq.SAVE),
            Key(R.drawable.ic_nano_run, green, R.string.terminal_nano_run, NanoProtocol.Seq.RUN, scriptOnly = true),
            Key(R.drawable.ic_nano_undo, blue, R.string.terminal_nano_undo, NanoProtocol.Seq.UNDO),
            Key(R.drawable.ic_nano_redo, blue, R.string.terminal_nano_redo, NanoProtocol.Seq.REDO),
            Key(R.drawable.ic_nano_find, accent, R.string.terminal_nano_find, NanoProtocol.Seq.FIND),
            Key(R.drawable.ic_nano_replace, accent, R.string.terminal_nano_replace, NanoProtocol.Seq.REPLACE),
            Key(R.drawable.ic_nano_select_all, accent, R.string.terminal_nano_select_all, NanoProtocol.Seq.SELECT_ALL),
            Key(R.drawable.ic_nano_goto, accent, R.string.terminal_nano_goto, NanoProtocol.Seq.GOTO),
            Key(R.drawable.ic_nano_fix, amber, R.string.terminal_nano_fix, NanoProtocol.Seq.FIX, scriptOnly = true),
            Key(R.drawable.ic_nano_quit, red, R.string.terminal_nano_quit, NanoProtocol.Seq.QUIT),
        )
        return all.filter { script || !it.scriptOnly }
    }

    private fun runTint(key: Char): Int = when (key) {
        '1' -> green
        '2' -> blue
        'a', '3' -> amber
        'e' -> blue
        else -> red
    }

    private fun iconKey(k: Key): ImageView = ImageView(context).apply {
        setImageResource(k.icon)
        setColorFilter(k.tint)
        contentDescription = context.getString(k.desc)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(9), dp(7), dp(9), dp(7))
        isFocusable = false
        isFocusableInTouchMode = false
        background = keyBackground(k.tint, strong = false)
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            send(k.seq)
        }
    }

    private fun labelKey(res: Int, tint: Int, seq: String, strong: Boolean): TextView =
        labelText(context.getString(res), tint, seq, strong)

    private fun labelText(text: String, tint: Int, seq: String, strong: Boolean): TextView = TextView(context).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
        setTextColor(tint)
        isFocusable = false
        isFocusableInTouchMode = false
        background = keyBackground(tint, strong)
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            send(seq)
        }
    }

    private fun cell(widthDp: Int) = LayoutParams(dp(widthDp), dp(KEY_H)).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) }

    private fun keyBackground(tint: Int, strong: Boolean): RippleDrawable {
        val content = GradientDrawable().apply {
            setColor(ColorUtils.setAlphaComponent(tint, if (strong) 0x55 else 0x22))
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), ColorUtils.setAlphaComponent(tint, if (strong) 0xFF else 0x88))
        }
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(8).toFloat()
        }
        return RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(tint, 0x55)), content, mask)
    }

    private fun rounded(fill: Int, stroke: Int, strokeDp: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(strokeDp), stroke)
    }

    private fun color(id: Int): Int = ContextCompat.getColor(context, id)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val POLL_MS = 300L
        const val KEY_W = 44
        const val PROMPT_W = 96
        const val RUN_W = 84
        const val KEY_H = 36
    }
}
