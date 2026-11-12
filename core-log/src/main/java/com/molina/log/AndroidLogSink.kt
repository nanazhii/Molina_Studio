package com.molina.log

import android.util.Log

class AndroidLogSink : LogSink {
    override fun write(entry: LogEntry) {
        val msg = entry.throwable?.let { entry.message + "\n" + it.stackTraceToString() } ?: entry.message
        Log.println(entry.level.priority, entry.tag.take(23), msg)
    }
}
