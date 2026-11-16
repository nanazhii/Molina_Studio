// molina-27v
package com.molina.files

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.molina.core.common.MolinaResult
import com.molina.core.storage.FileProps
import com.molina.core.storage.MolinaStorage
import com.molina.core.ui.ExtLabel
import com.molina.core.ui.FileKind
import com.molina.core.ui.TreeIconView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import com.molina.core.ui.R as UiR

/**
 * [molina-27v] Properti sebagai lembar bawah gaya Molina Cinema: ikon besar di kepala, baris label/nilai dari
 * [PropsText.rows], tombol "Salin path" dan "SHA-256" (hanya satu berkas biasa). Logika teks tetap di PropsText.
 * Hash dihitung di storage (thread IO) dan dibatalkan saat lembar ditutup.
 */
internal class PropsSheet(
    private val host: Fragment,
    private val storage: MolinaStorage,
    private val root: File,
    private val items: List<FileProps>,
) {
    private var hashJob: Job? = null

    private fun absolute(p: FileProps): String = if (p.path.isEmpty()) root.absolutePath else File(root, p.path).absolutePath

    fun show() {
        val ctx = host.requireContext()
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val bg = ContextCompat.getColor(ctx, UiR.color.molina_bg)
        val white = ContextCompat.getColor(ctx, UiR.color.molina_on_surface)
        val dim = ContextCompat.getColor(ctx, UiR.color.molina_on_surface_variant)
        val accent = ContextCompat.getColor(ctx, UiR.color.molina_cinema_accent)
        val onAccent = ContextCompat.getColor(ctx, UiR.color.molina_on_cinema_accent)
        val tile = ContextCompat.getColor(ctx, UiR.color.molina_surface_variant)

        val single = items.singleOrNull()
        val rows = PropsText.rows(items, view = PropsText.View("Internal", root.absolutePath))
        val hashTarget = single?.takeIf { !it.isDirectory && !it.isSymlink }
        val sheet = BottomSheetDialog(ctx)

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
            background = GradientDrawable().apply {
                setColor(bg)
                cornerRadii = floatArrayOf(dp(18).toFloat(), dp(18).toFloat(), dp(18).toFloat(), dp(18).toFloat(), 0f, 0f, 0f, 0f)
            }
        }

        // kepala: ikon besar + nama + ringkasan
        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val iconBox = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(tile)
                cornerRadius = dp(14).toFloat()
            }
        }
        val icon = TreeIconView(ctx)
        if (single != null) {
            icon.isDir = single.isDirectory
            icon.kind = if (single.isDirectory) FileKind.OTHER else FileKind.of(single.name)
            icon.label = if (single.isDirectory) "" else ExtLabel.of(single.name)
        } else {
            icon.isDir = true
        }
        iconBox.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))
        head.addView(iconBox, LinearLayout.LayoutParams(dp(72), dp(72)))
        val titles = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(
            TextView(ctx).apply {
                text = PropsText.title(items, "Internal")
                textSize = 18f
                setTextColor(white)
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            },
        )
        val total = items.sumOf { it.sizeBytes }
        val kindText = rows.firstOrNull { it.first == "Jenis" }?.second ?: "${items.size} item"
        titles.addView(
            TextView(ctx).apply {
                text = kindText + " \u00B7 " + FileSizeFormat.format(total)
                textSize = 13f
                setTextColor(accent)
                setPadding(0, dp(4), 0, 0)
            },
        )
        head.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) })
        col.addView(head)

        // baris label/nilai
        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        rows.forEach { (label, value) ->
            list.addView(
                TextView(ctx).apply {
                    text = label
                    textSize = 12f
                    setTextColor(dim)
                    setPadding(0, dp(12), 0, 0)
                },
            )
            list.addView(
                TextView(ctx).apply {
                    text = value
                    textSize = 14f
                    setTextColor(white)
                    setTextIsSelectable(true)
                    if (label == "Path" || label == "Target tautan") typeface = Typeface.MONOSPACE
                },
            )
        }
        val hashText = TextView(ctx).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(white)
            setTextIsSelectable(true)
            setPadding(0, dp(12), 0, 0)
            visibility = View.GONE
        }
        list.addView(hashText)
        col.addView(
            NestedScrollView(ctx).apply { addView(list) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) },
        )

        // tombol
        fun button(label: String, filled: Boolean): TextView = TextView(ctx).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (filled) onAccent else accent)
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                if (filled) setColor(accent) else {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(1), accent)
                }
            }
            isClickable = true
        }
        val actions = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val copyBtn = button("Salin path", filled = false)
        copyBtn.setOnClickListener { copy(ctx, items.joinToString("\n") { absolute(it) }, "Path disalin") }
        actions.addView(copyBtn, LinearLayout.LayoutParams(0, dp(46), 1f))
        if (hashTarget != null) {
            val hashBtn = button("SHA-256", filled = true)
            var digest: String? = null
            hashBtn.setOnClickListener {
                digest?.let { copy(ctx, it, "SHA-256 disalin"); return@setOnClickListener }
                if (hashJob?.isActive == true) {
                    hashJob?.cancel()
                    return@setOnClickListener
                }
                hashText.visibility = View.VISIBLE
                hashText.text = "SHA-256: menghitung\u2026"
                hashBtn.text = "Batal"
                hashJob = host.viewLifecycleOwner.lifecycleScope.launch {
                    var lastPct = -1
                    try {
                        val r = storage.sha256(hashTarget.path) { done, totalBytes ->
                            val pct = if (totalBytes > 0) (done * 100 / totalBytes).toInt() else 0
                            if (pct != lastPct) {
                                lastPct = pct
                                hashText.post { hashText.text = "SHA-256: menghitung\u2026 $pct%" } // dari thread IO
                            }
                        }
                        when (r) {
                            is MolinaResult.Success -> {
                                FilesEvents.propsHash("ok")
                                digest = r.value
                                hashText.text = "SHA-256:\n" + r.value
                                hashBtn.text = "Salin hash"
                            }
                            is MolinaResult.Failure -> {
                                FilesEvents.failed("hash", r.error, hashTarget.path)
                                hashText.text = "SHA-256 gagal: " + FilesErrorText.forOp(r.error.kind)
                                hashBtn.text = "SHA-256"
                            }
                        }
                    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                        FilesEvents.propsHash("cancelled")
                        hashText.text = "SHA-256 dibatalkan"
                        hashBtn.text = "SHA-256"
                        throw e
                    }
                }
            }
            actions.addView(hashBtn, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(10) })
        }
        col.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) })

        sheet.setContentView(col)
        (col.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        sheet.setOnShowListener {
            sheet.behavior.skipCollapsed = true
            sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        sheet.setOnDismissListener { hashJob?.cancel() }
        sheet.show()
    }

    private fun copy(ctx: Context, text: String, toast: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("Molina", text))
        Toast.makeText(ctx, toast, Toast.LENGTH_SHORT).show()
    }
}
