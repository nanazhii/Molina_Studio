// molina-patch25
package com.molina.terminal

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.molina.core.ui.R as UiR

/** Pecah/gabung nama berkas dan daftar ekstensi umum. Dotfile (.bashrc) dianggap tanpa ekstensi. */
internal object NewFileName {
    val EXTENSIONS = listOf(
        "sh", "py", "kt", "kts", "java", "json", "xml", "yml", "yaml", "toml", "ini", "conf",
        "log", "csv", "html", "css", "js", "c", "cpp", "h", "gradle", "properties", "md", "txt",
    )
    const val TEMPLATE = "skrip.sh"

    /** "skrip.tar.gz" -> ("skrip.tar", "gz"); ".bashrc" -> (".bashrc", ""); "README" -> ("README", ""). */
    fun split(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0 || dot == name.length - 1) name to "" else name.substring(0, dot) to name.substring(dot + 1)
    }

    fun join(stem: String, ext: String): String {
        val e = ext.trim().trimStart('.')
        return if (e.isEmpty()) stem.trim() else stem.trim() + "." + e
    }
}

/**
 * Dialog "Berkas baru" dari drawer: hitam pekat, bingkai abu-putih, kolom nama + ekstensi (dropdown),
 * tombol Batal / Buka di nano. Nama bawaan skrip.sh. Pola dari repo lama, ditulis ulang.
 */
internal fun showNewFileDialog(
    context: Context,
    folderLabel: String,
    onNano: (String) -> Unit,
) {
    val density = context.resources.displayMetrics.density
    fun dp(v: Int): Int = (v * density + 0.5f).toInt()
    val frame = ContextCompat.getColor(context, R.color.molina_nano_frame)
    val dim = 0xFFA3A8B0.toInt()
    val brand = ContextCompat.getColor(context, UiR.color.molina_tab_brand)
    val init = NewFileName.split(NewFileName.TEMPLATE)

    fun border(radiusDp: Int, strokeColor: Int, fill: Int? = null) = GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(1), strokeColor)
        if (fill != null) setColor(fill)
    }

    fun editor(hintRes: Int, text: String): EditText = EditText(context).apply {
        setSingleLine()
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_DONE
        typeface = Typeface.MONOSPACE
        textSize = 14f
        setTextColor(Color.WHITE)
        setHintTextColor(0x99A3A8B0.toInt())
        setHint(hintRes)
        setText(text)
        setPadding(dp(8), 0, dp(8), 0)
        gravity = Gravity.CENTER_VERTICAL
    }

    val stem = editor(R.string.newfile_name_hint, init.first)
    stem.background = border(6, ColorUtils.setAlphaComponent(frame, 0x8C))
    val ext = editor(R.string.newfile_ext_hint, init.second)
    ext.background = null
    ext.setPadding(0, 0, 0, 0)
    ext.filters = arrayOf(InputFilter { src, _, _, _, _, _ -> src.filter { c -> c != '/' && c != '\\' && !c.isWhitespace() } })

    val extBox = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = border(6, ColorUtils.setAlphaComponent(frame, 0x8C))
        setPadding(dp(8), 0, dp(4), 0)
    }
    extBox.addView(TextView(context).apply { text = "."; setTextColor(dim); typeface = Typeface.MONOSPACE; textSize = 14f })
    extBox.addView(ext, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    val arrow = TextView(context).apply {
        text = "\u25BE"
        setTextColor(frame)
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(dp(6), 0, dp(6), 0)
        isClickable = true
        setOnClickListener { anchor ->
            val menu = PopupMenu(context, anchor)
            menu.menu.add(0, 0, 0, R.string.newfile_ext_none)
            NewFileName.EXTENSIONS.forEachIndexed { i, e -> menu.menu.add(0, i + 1, i + 1, ".$e") }
            menu.setOnMenuItemClickListener { item ->
                ext.setText(if (item.itemId == 0) "" else NewFileName.EXTENSIONS[item.itemId - 1])
                true
            }
            menu.show()
        }
    }
    extBox.addView(arrow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))

    val title = TextView(context).apply {
        setText(R.string.drawer_new_file)
        setTextColor(Color.WHITE)
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
    }
    val folder = TextView(context).apply {
        text = context.getString(R.string.newfile_folder, folderLabel)
        setTextColor(dim)
        typeface = Typeface.MONOSPACE
        textSize = 11f
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }
    val fields = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    fields.addView(stem, LinearLayout.LayoutParams(0, dp(38), 1f))
    fields.addView(extBox, LinearLayout.LayoutParams(dp(124), dp(38)).apply { marginStart = dp(6) })

    val dialog = Dialog(context)
    fun name(): String = NewFileName.join(stem.text.toString(), ext.text.toString())
    fun button(textRes: Int, color: Int, bold: Boolean, onClick: () -> Unit) = TextView(context).apply {
        setText(textRes)
        setTextColor(color)
        textSize = 14f
        if (bold) typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        minHeight = dp(36)
        setPadding(dp(12), 0, dp(12), 0)
        isClickable = true
        setOnClickListener { onClick() }
    }
    val cancel = button(R.string.terminal_tab_cancel, dim, false) { dialog.dismiss() }
    val nano = button(R.string.newfile_open_nano, brand, true) {
        if (stem.text.isNotBlank()) { dialog.dismiss(); onNano(name()) }
    }
    val buttons = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        addView(cancel)
        addView(nano)
    }
    fun refresh() {
        val ok = stem.text.isNotBlank()
        nano.alpha = if (ok) 1f else 0.4f
    }
    stem.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = refresh()
    })
    val done = { _: TextView?, _: Int, _: android.view.KeyEvent? ->
        if (stem.text.isNotBlank()) { dialog.dismiss(); onNano(name()) }
        true
    }
    stem.setOnEditorActionListener(done)
    ext.setOnEditorActionListener(done)
    refresh()

    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = border(10, frame, Color.BLACK)
        setPadding(dp(14), dp(10), dp(14), dp(10))
    }
    root.addView(title)
    root.addView(folder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3) })
    root.addView(fields, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
    root.addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })

    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    dialog.setContentView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    dialog.show()
    val width = minOf((context.resources.displayMetrics.widthPixels * 0.92f).toInt(), dp(420))
    dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
    stem.requestFocus()
    stem.setSelection(0, stem.text.length)
}

// molina-patch27
/** Interpreter untuk item "Jalankan" di menu berkas, dan baris shell yang dikirim ke sesi. */
internal object RunnableFile {
    private class Spec(val command: String, val aptPackage: String)

    private val MAP = mapOf(
        "sh" to Spec("bash", "bash"),
        "py" to Spec("python3", "python3"),
        "js" to Spec("node", "nodejs"),
        "pl" to Spec("perl", "perl"),
        "rb" to Spec("ruby", "ruby"),
    )

    private fun spec(fileName: String): Spec? = MAP[NewFileName.split(fileName).second.lowercase()]

    fun interpreter(fileName: String): String? = spec(fileName)?.command

    /** Satu baris shell: cd ke folder berkas, jalankan; bila interpreter tidak ada, cetak petunjuk. Null bila ekstensi tidak dikenali. */
    fun shellLine(fileName: String, quotedDir: String, quotedFile: String, proot: Boolean, log: RunLog? = null): String? {
        val s = spec(fileName) ?: return null
        val cmd = if (!proot && s.command == "bash") "sh" else s.command
        val hint = if (proot) "$cmd belum terpasang. Pasang: apt install ${s.aptPackage}" else "$cmd tidak ditemukan di shell ini"
        val plain = "$cmd $quotedFile"
        val exec = if (log == null) {
            plain
        } else {
            val runner = if (cmd == "python3") "python3 -u" else cmd
            val piped = "$runner $quotedFile 2>&1 | tee ${log.quotedBase}-\$(date +%Y%m%d-%H%M%S).log"
            "if mkdir -p ${log.quotedDir} 2>/dev/null; then $piped; else echo '[Molina] folder log tidak bisa dibuat, dijalankan tanpa catatan'; $plain; fi"
        }
        // [RUN-SUMMARY] ringkasan sama dengan molina-run. Dengan log, status keluar pipeline milik tee, jadi hanya waktu yang dilaporkan.
        val start = "printf '\\033[1;32m▶ %s (%s)\\033[0m\\n' ${DrawerFs.quote(fileName)} $cmd; _mr_t=\$(date +%s); "
        val tail = if (log == null) {
            "; _mr_rc=\$?; _mr_d=\$((\$(date +%s)-_mr_t)); if [ \$_mr_rc -eq 0 ]; then printf '\\033[1;32m✔ selesai (%s dtk)\\033[0m\\n' \$_mr_d; " +
                "elif [ \$_mr_rc -eq 130 ]; then printf '\\033[1;31m✘ dihentikan (Ctrl+C)\\033[0m\\n'; " +
                "else printf '\\033[1;31m✘ keluar dengan kode %s (%s dtk)\\033[0m\\n' \$_mr_rc \$_mr_d; fi; unset _mr_t _mr_rc _mr_d"
        } else {
            "; _mr_d=\$((\$(date +%s)-_mr_t)); printf '\\033[1;32m■ selesai (%s dtk)\\033[0m\\n' \$_mr_d; unset _mr_t _mr_d"
        }
        return "cd $quotedDir && if command -v $cmd >/dev/null 2>&1; then $start$exec$tail; else echo '[Molina] $hint'; fi\r"
    }
}

// molina-patch28
/** Tujuan log keluaran Jalankan: folder log dan awalan nama berkas log (keduanya sudah dikutip untuk shell). */
internal class RunLog(val quotedDir: String, val quotedBase: String)
