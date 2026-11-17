package com.molina.engine.newpipe

import com.molina.log.LogLevel
import com.molina.log.MolinaLog
import com.molina.log.MolinaLogger

/** Jembatan log engine ke MolinaLogger. Tag: Molina/stream/<tag>. */
internal object StreamLog {
    fun log(source: String, level: LogLevel, tag: String, message: String) {
        MolinaLog.logger.log(level, MolinaLogger.tag("stream", tag), "[$source] $message")
    }
}
