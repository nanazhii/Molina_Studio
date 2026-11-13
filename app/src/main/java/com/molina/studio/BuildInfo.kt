package com.molina.studio

import android.content.Context
import com.molina.log.MolinaLog
import com.molina.log.MolinaLogger
import com.molina.settings.BuildLine

/** Satu baris identitas build untuk molina.log: versi, nomor commit, dan level API perangkat. */
internal object BuildInfo {
    private val logTag = MolinaLogger.tag("core")

    fun describe(ctx: Context): String = BuildLine.describe(ctx)

    fun log(ctx: Context) {
        MolinaLog.logger.i(logTag, describe(ctx))
    }
}
