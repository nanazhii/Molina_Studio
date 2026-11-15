// molina-patch26k2
package com.molina.files

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox // molina-27q
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.molina.core.common.PasswordPolicy // molina-27q
import com.molina.core.storage.ArchiveFormat
import com.molina.core.storage.ConflictPolicy // 26k4:e43
import com.molina.core.storage.ExtractTarget
import com.molina.core.storage.ArchiveNames
import com.molina.core.ui.MolinaDialog
import com.molina.core.ui.R as UiR

/** Dialog Arsip (kata sandi dan opsi Kompres) bergaya MolinaDialog: hitam pekat, bingkai abu-putih. */
internal object ArchiveDialogs {
    private fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    private fun label(c: Context, text: String): TextView = TextView(c).apply {
        this.text = text
        setTextColor(ContextCompat.getColor(c, UiR.color.molina_on_surface_variant))
        textSize = 13f
        setPadding(0, dp(c, 14), 0, dp(c, 2))
    }

    private fun field(c: Context, hintText: String, password: Boolean): EditText = EditText(c).apply {
        hint = hintText
        setHintTextColor(ContextCompat.getColor(c, UiR.color.molina_on_surface_variant))
        setTextColor(Color.WHITE)
        textSize = 15f
        background = ContextCompat.getDrawable(c, UiR.drawable.molina_bg_box)
        setPadding(dp(c, 12), 0, dp(c, 12), 0)
        setSingleLine(true)
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        inputType = if (password) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT
        }
        imeOptions = EditorInfo.IME_ACTION_DONE
    }

    private fun fieldParams(c: Context): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 44)).apply { topMargin = dp(c, 14) }

    /** Satu baris RadioButton yang bisa digeser mendatar. [onChecked] menerima indeks pilihan. */
    private fun radioRow(c: Context, labels: List<String>, checked: Int, onChecked: (Int) -> Unit): View {
        val group = RadioGroup(c).apply { orientation = RadioGroup.HORIZONTAL }
        labels.forEach { text ->
            group.addView(
                RadioButton(c).apply {
                    id = View.generateViewId()
                    this.text = text
                    setTextColor(Color.WHITE)
                },
            )
        }
        group.check(group.getChildAt(checked.coerceIn(0, labels.lastIndex)).id)
        group.setOnCheckedChangeListener { g, id -> onChecked(g.indexOfChild(g.findViewById<View>(id))) }
        return HorizontalScrollView(c).apply {
            isHorizontalScrollBarEnabled = false
            addView(group)
        }
    }

    /** Minta kata sandi arsip. [onSubmit] menerima kata sandi (tidak kosong); tidak pernah dicatat atau disimpan. */
    fun password(context: Context, title: String, message: String, onCancel: () -> Unit = {}, onSubmit: (String) -> Unit) { // 26k4:e44
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        box.addView(
            TextView(context).apply {
                text = message
                setTextColor(ContextCompat.getColor(context, UiR.color.molina_on_surface_variant))
                textSize = 14f
                setPadding(0, dp(context, 10), 0, 0)
            },
        )
        val input = field(context, "Kata sandi", password = true)
        box.addView(input, fieldParams(context))
        val submit: () -> Boolean = {
            val pw = input.text.toString()
            if (pw.isEmpty()) {
                input.error = "Isi kata sandi"
                false
            } else {
                onSubmit(pw)
                true
            }
        }
        MolinaDialog.form(context, title, "Buka", box, submit, onCancel) // 26k4:e45
    }

    /**
     * Opsi Ekstrak untuk banyak arsip atau "Ekstrak ke…": tujuan (di sini / folder bernama arsip) dan kebijakan bila
     * berkas sudah ada. Pilihan Timpa selalu diikuti dialog konfirmasi. 26k4:e46
     */
    fun extractOptions(context: Context, count: Int, onSubmit: (ExtractTarget, ConflictPolicy) -> Unit) {
        var target = ArchiveOptions.TARGETS.first()
        var policy = ArchiveOptions.POLICIES.first()
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        box.addView(label(context, "Tujuan"))
        box.addView(
            radioRow(context, ArchiveOptions.TARGETS.map { ArchiveOptions.targetLabel(it) }, 0) { i ->
                target = ArchiveOptions.TARGETS[i]
            },
        )
        box.addView(label(context, "Jika berkas sudah ada"))
        box.addView(
            radioRow(context, ArchiveOptions.POLICIES.map { ArchiveOptions.policyLabel(it) }, 0) { i ->
                policy = ArchiveOptions.POLICIES[i]
            },
        )
        val submit: () -> Boolean = {
            val chosenTarget = target
            val chosenPolicy = policy
            if (chosenPolicy == ConflictPolicy.OVERWRITE) {
                MolinaDialog.confirm(
                    context,
                    "Timpa berkas yang sudah ada?",
                    ArchiveText.overwriteWarning(),
                    "Timpa",
                ) { onSubmit(chosenTarget, chosenPolicy) }
            } else {
                onSubmit(chosenTarget, chosenPolicy)
            }
            true
        }
        val title = if (count > 1) "Ekstrak $count arsip" else "Ekstrak arsip"
        MolinaDialog.form(context, title, "Ekstrak", box, submit)
    }

    /**
     * Opsi Kompres: nama, format, level, dan kata sandi (ZIP dan 7z, AES-256, diulang untuk mencegah salah ketik).
     * 7z: sandi lemah ditolak ([PasswordPolicy]) dan nama berkas bisa ikut dienkripsi.
     * [onSubmit] (format, level, nama dasar tanpa ekstensi, kata sandi atau null, enkripsi nama).
     */
    fun compress(
        context: Context,
        defaultName: String,
        onSubmit: (ArchiveFormat, Int, String, String?, Boolean) -> Unit,
    ) {
        val formats = ArchiveOptions.FORMATS
        var format = formats.first()
        var level = format.defaultLevel

        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val name = field(context, "Nama arsip", password = false).apply {
            setText(defaultName)
            setSelection(text.length)
        }
        box.addView(name, fieldParams(context))

        val levelTitle = label(context, "Kompresi")
        val levelHolder = FrameLayout(context)
        val pwBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val pw1 = field(context, "Kata sandi (opsional, AES-256)", password = true)
        val pw2 = field(context, "Ulangi kata sandi", password = true)
        pwBox.addView(pw1, fieldParams(context))
        pwBox.addView(pw2, fieldParams(context))
        val encNames = CheckBox(context).apply { // molina-27q
            text = "Enkripsi nama berkas (daftar isi ikut terkunci)"
            setTextColor(Color.WHITE)
        }
        pwBox.addView(encNames)

        fun applyFormat() {
            level = format.defaultLevel
            levelHolder.removeAllViews()
            val options = ArchiveOptions.levels(format)
            val has = options.isNotEmpty()
            levelTitle.visibility = if (has) View.VISIBLE else View.GONE
            levelHolder.visibility = if (has) View.VISIBLE else View.GONE
            if (has) {
                val idx = options.indexOfFirst { it.first == level }.coerceAtLeast(0)
                level = options[idx].first
                levelHolder.addView(radioRow(context, options.map { it.second }, idx) { i -> level = options[i].first })
            }
            pwBox.visibility = if (format.canEncrypt) View.VISIBLE else View.GONE
            encNames.visibility = if (format.encryptsNames) View.VISIBLE else View.GONE // molina-27q
        }

        box.addView(label(context, "Format"))
        box.addView(
            radioRow(context, formats.map { it.label }, 0) { i ->
                format = formats[i]
                applyFormat()
            },
        )
        box.addView(levelTitle)
        box.addView(levelHolder)
        box.addView(pwBox)
        applyFormat()

        val validate: () -> Boolean = validate@{
            val base = ArchiveNames.cleanBaseName(name.text.toString(), format)
            val problem = ArchiveNames.baseNameProblem(base, format)
            if (problem != null) {
                name.error = ArchiveText.nameProblem(problem)
                return@validate false
            }
            var password: String? = null
            if (format.canEncrypt) {
                val a = pw1.text.toString()
                if (a.isNotEmpty()) {
                    if (a != pw2.text.toString()) {
                        pw2.error = "Kata sandi tidak sama"
                        return@validate false
                    }
                    if (format == ArchiveFormat.SEVEN_Z) { // molina-27q: kunci 7z murah ditebak, jadi sandi lemah ditolak
                        val verdict = PasswordPolicy.evaluate(a)
                        if (!verdict.acceptable) {
                            pw1.error = verdict.message
                            return@validate false
                        }
                    }
                    password = a
                }
            }
            onSubmit(format, format.clampLevel(level), base, password, format.encryptsNames && password != null && encNames.isChecked)
            true
        }
        MolinaDialog.form(context, "Kompres", "Kompres", box, validate)
    }
}
