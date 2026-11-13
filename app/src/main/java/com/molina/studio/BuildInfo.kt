package com.molina.studio

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.molina.log.MolinaLog
import com.molina.log.MolinaLogger
import com.molina.settings.BuildConfig as SettingsBuildConfig

/** Satu baris identitas build untuk molina.log: versi, nomor commit, dan level API perangkat. */
internal object BuildInfo {
    private val logTag = MolinaLogger.tag("core")

    fun describe(ctx: Context): String {
        val (name, code) = try {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            (info.versionName ?: "?") to PackageInfoCompat.getLongVersionCode(info)
        } catch (e: PackageManager.NameNotFoundException) {
            "?" to 0L
        }
        return "Molina $name ($code) \u00b7 ${SettingsBuildConfig.COMMIT_SHA} \u00b7 API ${Build.VERSION.SDK_INT}"
    }

    fun log(ctx: Context) {
        MolinaLog.logger.i(logTag, describe(ctx))
    }
}
