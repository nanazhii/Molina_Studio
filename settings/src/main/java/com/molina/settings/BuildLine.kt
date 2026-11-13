package com.molina.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat

/** Baris identitas build: versi, kode, nomor commit, dan level API perangkat. */
object BuildLine {
    fun describe(ctx: Context): String {
        val (name, code) = try {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            (info.versionName ?: "?") to PackageInfoCompat.getLongVersionCode(info)
        } catch (e: PackageManager.NameNotFoundException) {
            "?" to 0L
        }
        return "Molina $name ($code) \u00b7 ${BuildConfig.COMMIT_SHA} \u00b7 API ${Build.VERSION.SDK_INT}"
    }
}
