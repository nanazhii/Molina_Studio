package com.molina.files

import java.util.Locale

object FileSizeFormat {
    private val units = arrayOf("KB", "MB", "GB", "TB")

    fun format(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        var value = bytes.toDouble()
        var i = -1
        do {
            value /= 1024
            i++
        } while (value >= 1024 && i < units.lastIndex)
        return String.format(Locale.US, "%.1f %s", value, units[i])
    }
}
