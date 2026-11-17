package com.molina.player

import android.util.Log
import com.molina.core.stream.LogRedact
import com.molina.log.LogLevel
import com.molina.log.MolinaLog
import com.molina.log.MolinaLogger

/**
 * Pengganti android.util.Log untuk modul mpv. Pesan disamarkan (URL, token) lalu dikirim ke
 * MolinaLogger (logcat + berkas). Tag seragam: Molina/mpv/<tag>.
 */
internal object MpvLog {
    private const val AREA = "mpv"

    fun v(tag: String, msg: String, tr: Throwable? = null) = emit(LogLevel.DEBUG, tag, msg, tr)
    fun d(tag: String, msg: String, tr: Throwable? = null) = emit(LogLevel.DEBUG, tag, msg, tr)
    fun i(tag: String, msg: String, tr: Throwable? = null) = emit(LogLevel.INFO, tag, msg, tr)
    fun w(tag: String, msg: String, tr: Throwable? = null) = emit(LogLevel.WARN, tag, msg, tr)
    fun w(tag: String, tr: Throwable) = emit(LogLevel.WARN, tag, "", tr)
    fun e(tag: String, msg: String, tr: Throwable? = null) = emit(LogLevel.ERROR, tag, msg, tr)
    fun e(tag: String, tr: Throwable) = emit(LogLevel.ERROR, tag, "", tr)

    private fun emit(level: LogLevel, tag: String, msg: String, tr: Throwable?) {
        val safe = LogRedact.text(msg)
        val trace = if (tr == null) "" else "\n" + LogRedact.text(Log.getStackTraceString(tr))
        val area = if (tag.isEmpty() || tag == AREA) null else tag.take(24)
        MolinaLog.logger.log(level, MolinaLogger.tag(AREA, area), safe + trace)
    }
}
