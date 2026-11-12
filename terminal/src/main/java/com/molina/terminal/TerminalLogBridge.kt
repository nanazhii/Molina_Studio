// molina-patch8
package com.molina.terminal

import com.molina.log.MolinaLog
import com.molina.log.MolinaLogger

/** Jembatan log emulator/view ke MolinaLog (satu tag grup: terminal). Verbose sengaja dibuang. */
internal object TerminalLogBridge {
    private val tag = MolinaLogger.tag("terminal")

    fun e(src: String?, msg: String?, t: Throwable? = null) = MolinaLog.logger.e(tag, "${src.orEmpty()}: ${msg.orEmpty()}", t)
    fun w(src: String?, msg: String?) = MolinaLog.logger.w(tag, "${src.orEmpty()}: ${msg.orEmpty()}")
    fun i(src: String?, msg: String?) = MolinaLog.logger.i(tag, "${src.orEmpty()}: ${msg.orEmpty()}")
    fun d(src: String?, msg: String?) = MolinaLog.logger.d(tag, "${src.orEmpty()}: ${msg.orEmpty()}")
}
