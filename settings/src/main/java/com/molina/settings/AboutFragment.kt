package com.molina.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.molina.core.ui.R as UiR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Layar Tentang: wordmark Molina, nomor versi, dan nama paket. */
class AboutFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val d = resources.displayMetrics.density
        val (name, code) = versionInfo()
        val wordmark = ImageView(ctx).apply {
            setImageResource(UiR.drawable.molina_wordmark)
            contentDescription = "Molina"
        }
        val version = TextView(ctx).apply {
            text = "Versi $name ($code) \u00b7 ${BuildConfig.COMMIT_SHA}"
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, (12 * d).toInt(), 0, 0)
        }
        val tagline = TextView(ctx).apply {
            text = "Molina Studio: Terminal dan Cinema"
            textSize = 13f
            gravity = Gravity.CENTER
            alpha = 0.7f
            setPadding(0, (4 * d).toInt(), 0, 0)
        }
        val pkg = TextView(ctx).apply {
            text = ctx.packageName
            typeface = Typeface.MONOSPACE
            textSize = 11f
            gravity = Gravity.CENTER
            alpha = 0.5f
            setPadding(0, (24 * d).toInt(), 0, 0)
        }
        val copy = MaterialButton(ctx).apply {
            text = "Salin info build"
            setOnClickListener { copyBuildInfo(ctx) }
        }
        val report = MaterialButton(ctx).apply {
            text = "Salin laporan masalah"
            setOnClickListener { copyReport(ctx) }
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((24 * d).toInt(), (24 * d).toInt(), (24 * d).toInt(), (24 * d).toInt())
            addView(wordmark, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(version)
            addView(tagline)
            addView(pkg)
            addView(copy)
            addView(report)
        }
    }

    private fun copyBuildInfo(ctx: Context) =
        putClip(ctx, "Molina build", BuildLine.describe(ctx), "Info build disalin")

    /** Info build + ringkasan error per area + 50 entri log terakhir, dibaca di thread IO. */
    private fun copyReport(ctx: Context) {
        viewLifecycleOwner.lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) { LogReportSource.create(ctx) }
            putClip(ctx, "Molina laporan", text, "Laporan masalah disalin")
        }
    }

    private fun putClip(ctx: Context, label: String, text: String, notice: String) {
        val clipboard = ctx.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(ctx, notice, Toast.LENGTH_SHORT).show()
        }
    }

    private fun versionInfo(): Pair<String, Long> = try {
        val ctx = requireContext()
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        (info.versionName ?: "?") to PackageInfoCompat.getLongVersionCode(info)
    } catch (e: PackageManager.NameNotFoundException) {
        "?" to 0L
    }
}
