package com.molina.log

import android.content.Context
import java.io.File

object MolinaLogSetup {
    const val PUBLIC_DIR = "/storage/emulated/0/Molina/logs"

    /** Panggil sekali di Application.onCreate(). */
    fun init(context: Context) {
        val dir = resolveDir(context)
        MolinaLog.install(MolinaLogger(listOf(AndroidLogSink(), FileLogSink(dir))))
        MolinaLog.logger.i(MolinaLogger.tag("core"), "Logger aktif, file log: ${dir.absolutePath}")
    }

    /** Utamakan folder publik Molina; jika belum ada izin, pakai folder khusus aplikasi. */
    private fun resolveDir(context: Context): File {
        val pub = File(PUBLIC_DIR)
        if ((pub.isDirectory || pub.mkdirs()) && pub.canWrite()) return pub
        return context.getExternalFilesDir("logs") ?: File(context.filesDir, "logs")
    }
}
