package com.molina.core.common.stream

import com.molina.core.common.StreamSearchItem
import java.util.Locale

/** Teks baris hasil pencarian judul: judul, lalu pengunggah, durasi, dan waktu unggah. Murni, tanpa Android. */
object SearchLabel {
    fun of(item: StreamSearchItem): String {
        val meta = listOfNotNull(
            item.uploader?.takeIf { it.isNotBlank() },
            item.durationSec?.takeIf { it > 0 }?.let(::duration),
            item.uploadedText?.takeIf { it.isNotBlank() },
        ).joinToString(" \u00B7 ")
        val title = item.title.trim().ifEmpty { "(tanpa judul)" }
        return if (meta.isEmpty()) title else "$title\n$meta"
    }

    /** `m:ss`, atau `h:mm:ss` mulai satu jam. */
    fun duration(totalSec: Long): String {
        val s = totalSec.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) else String.format(Locale.ROOT, "%d:%02d", m, sec)
    }
}
