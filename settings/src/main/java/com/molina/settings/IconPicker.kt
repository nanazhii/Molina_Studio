package com.molina.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.molina.log.MolinaLog
import com.molina.log.MolinaLogger

/**
 * Pemilih ikon peluncur lewat activity-alias di AndroidManifest app.
 * Nama komponen mengikuti namespace app (com.molina.studio); ubah di sini bila namespace berganti.
 */
internal object IconPicker {
    private const val GREEN = "com.molina.studio.IconGreen"
    private const val AMBER = "com.molina.studio.IconAmber"
    private val logTag = MolinaLogger.tag("settings")

    fun build(ctx: Context): View {
        val d = ctx.resources.displayMetrics.density
        val title = TextView(ctx).apply {
            text = "Ikon aplikasi"
            textSize = 16f
            setPadding(0, (16 * d).toInt(), 0, (8 * d).toInt())
        }
        val green = option(ctx, "Terminal", GREEN)
        val amber = option(ctx, "Cinema", AMBER)
        val group = MaterialButtonToggleGroup(ctx).apply {
            isSingleSelection = true
            isSelectionRequired = true
            addView(green)
            addView(amber)
            check(if (isAmberActive(ctx)) amber.id else green.id)
            addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) select(ctx, amber = checkedId == amber.id)
            }
        }
        val hint = TextView(ctx).apply {
            text = "Ikon di layar utama bisa butuh beberapa detik untuk berganti."
            textSize = 12f
            alpha = 0.7f
            setPadding(0, (8 * d).toInt(), 0, 0)
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(group)
            addView(hint)
        }
    }

    private fun option(ctx: Context, label: String, alias: String): MaterialButton {
        val d = ctx.resources.displayMetrics.density
        return MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            id = View.generateViewId()
            text = label
            iconOf(ctx, alias)?.let {
                icon = it
                iconTint = null
                iconSize = (32 * d).toInt()
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            }
        }
    }

    private fun component(ctx: Context, alias: String) = ComponentName(ctx.packageName, alias)

    private fun iconOf(ctx: Context, alias: String): Drawable? = try {
        val pm = ctx.packageManager
        pm.getActivityInfo(component(ctx, alias), PackageManager.MATCH_DISABLED_COMPONENTS).loadIcon(pm)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun isAmberActive(ctx: Context): Boolean =
        ctx.packageManager.getComponentEnabledSetting(component(ctx, AMBER)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    /** Aktifkan alias tujuan lebih dulu, baru matikan yang lama, agar selalu ada satu pintu peluncur. */
    private fun select(ctx: Context, amber: Boolean) {
        val pm = ctx.packageManager
        val on = if (amber) AMBER else GREEN
        val off = if (amber) GREEN else AMBER
        try {
            pm.setComponentEnabledSetting(component(ctx, on), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(component(ctx, off), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            MolinaLog.logger.i(logTag, "ikon peluncur: " + (if (amber) "Cinema" else "Terminal"))
        } catch (e: SecurityException) {
            MolinaLog.logger.e(logTag, "gagal mengganti ikon: " + e.message)
        }
    }
}
