package com.molina.picker

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import com.molina.core.common.diag.Diag
import com.molina.core.common.picker.PickKind
import com.molina.core.ui.R as UiR
import com.molina.filemanager.FilePick
import com.molina.filemanager.FileManagerEmbed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File

/**
 * Dialog layar penuh berisi file manager Molina (mode tertanam). FILE: ketuk berkas = terpilih.
 * FOLDER: tombol "Pilih folder ini" memilih folder aktif. Back sistem naik folder dulu, baru menutup.
 */
internal class PickerDialog(
    private val context: Context,
    private val kind: PickKind,
    private val startDir: File?,
    private val onResult: (File?) -> Unit,
) {
    @Suppress("DEPRECATION") // systemWindowInset*: satu-satunya API inset sebelum API 30; minSdk 26
    fun show() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var result: File? = null
        lateinit var dialog: Dialog
        fun finish(f: File?) {
            result = f
            dialog.dismiss()
        }

        val pick = if (kind == PickKind.FILE) FilePick { f -> finish(f) } else null
        val handle = FileManagerEmbed.create(context, scope, pick = pick)

        val surface = context.getColor(UiR.color.molina_surface)
        val accent = context.getColor(UiR.color.molina_terminal_accent)

        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        fun button(labelRes: Int, onClick: () -> Unit) = Button(context, null, android.R.attr.borderlessButtonStyle).apply {
            setText(labelRes)
            setAllCaps(false)
            setTextColor(accent)
            setOnClickListener { onClick() }
        }
        bar.addView(button(R.string.picker_cancel) { finish(null) })
        if (kind == PickKind.FOLDER) bar.addView(button(R.string.picker_choose_folder) { finish(handle.activeDirectory()) })

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surface)
            setOnApplyWindowInsetsListener { v, insets ->
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }
        }
        root.addView(handle.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        dialog = object : Dialog(context, android.R.style.Theme_DeviceDefault_NoActionBar) {
            override fun onBackPressed() {
                if (!handle.navigateUp()) super.onBackPressed()
            }
        }
        dialog.setContentView(root)
        dialog.setOnDismissListener {
            scope.cancel()
            val picked = result
            if (picked != null) {
                Diag.behavior("picker", "picked", "pilihan diterima", mapOf("kind" to kind.name.lowercase()))
            } else {
                Diag.behavior("picker", "cancelled", "pemilih ditutup tanpa pilihan", mapOf("kind" to kind.name.lowercase()))
            }
            onResult(picked)
        }
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
        Diag.behavior("picker", "shown", "pemilih dibuka", mapOf("kind" to kind.name.lowercase()))
        startDir?.takeIf { it.isDirectory }?.let { dir -> handle.view.post { handle.openDirectory(dir) } }
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}
