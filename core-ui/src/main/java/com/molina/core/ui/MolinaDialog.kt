// molina-patch26d
package com.molina.core.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView

/**
 * Dialog hitam pekat berbingkai abu-putih untuk input nama dan konfirmasi, dipakai bersama modul
 * (dipindah dari :files). Nama awal diblok tanpa ekstensi bila selectBase.
 */
object MolinaDialog {

    /** Nama awal diblok tanpa ekstensi bila [selectBase] (mis. saat Ganti nama berkas). */
    fun input(
        context: Context,
        title: String,
        initial: String,
        positive: String,
        selectBase: Boolean,
        onSubmit: (String) -> Unit,
    ) {
        show(context, title, null, true, initial, selectBase, positive) { onSubmit(it) }
    }

    fun confirm(context: Context, title: String, message: String, positive: String, onConfirm: () -> Unit) {
        show(context, title, message, false, "", false, positive) { onConfirm() }
    }

    // molina-patch26j
    /** Pegangan dialog info: pesan bisa diperbarui dari thread mana pun. */
    class Info internal constructor(private val dialog: Dialog, private val message: TextView) {
        fun setMessage(text: String) {
            message.post { message.text = text }
        }

        fun dismiss() = dialog.dismiss()
    }

    /**
     * Dialog informasi hitam pekat. [secondary] (opsional) menjadi tombol kiri dan tidak menutup dialog;
     * [onDismiss] dipanggil saat dialog tertutup dengan cara apa pun. Teks bisa diseleksi dan disalin.
     */
    fun info(
        context: Context,
        title: String,
        message: String,
        positive: String,
        secondary: String? = null,
        onSecondary: (Info) -> Unit = {},
        onDismiss: () -> Unit = {},
    ): Info {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.molina_dialog, null, false)
        view.findViewById<TextView>(R.id.dlg_title).text = title
        val msg = view.findViewById<TextView>(R.id.dlg_message)
        msg.text = message
        msg.setTextIsSelectable(true)
        msg.visibility = android.view.View.VISIBLE
        val ok = view.findViewById<TextView>(R.id.dlg_ok)
        ok.text = positive
        ok.setOnClickListener { dialog.dismiss() }
        val info = Info(dialog, msg)
        val second = view.findViewById<TextView>(R.id.dlg_cancel)
        if (secondary != null) {
            second.text = secondary
            second.setOnClickListener { onSecondary(info) }
        } else {
            second.visibility = android.view.View.GONE
        }
        dialog.setOnDismissListener { onDismiss() }
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        return info
    }

    private fun show(
        context: Context,
        title: String,
        message: String?,
        withInput: Boolean,
        initial: String,
        selectBase: Boolean,
        positive: String,
        onPositive: (String) -> Unit,
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.molina_dialog, null, false)
        view.findViewById<TextView>(R.id.dlg_title).text = title
        val msg = view.findViewById<TextView>(R.id.dlg_message)
        if (message != null) {
            msg.text = message
            msg.visibility = android.view.View.VISIBLE
        }
        val input = view.findViewById<EditText>(R.id.dlg_input)
        val ok = view.findViewById<TextView>(R.id.dlg_ok)
        ok.text = positive
        view.findViewById<android.view.View>(R.id.dlg_cancel).setOnClickListener { dialog.dismiss() }
        val submit = {
            val text = if (withInput) input.text.toString() else ""
            dialog.dismiss()
            onPositive(text)
        }
        ok.setOnClickListener { submit() }
        if (withInput) {
            input.visibility = android.view.View.VISIBLE
            input.setText(initial)
            val dot = initial.lastIndexOf('.')
            input.setSelection(0, if (selectBase && dot > 0) dot else initial.length)
            input.setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) {
                    submit()
                    true
                } else {
                    false
                }
            }
            input.requestFocus()
        }
        dialog.setContentView(view)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            if (withInput) setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
        dialog.show()
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
}
