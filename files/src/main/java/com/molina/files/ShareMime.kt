// molina-patch26j
package com.molina.files

import java.util.Locale

/** Penentuan tipe MIME untuk Bagikan. Murni JVM; pemetaan ekstensi sistem ada di [FilesShare]. */
internal object ShareMime {
    const val FALLBACK = "application/octet-stream"

    private val EXTRA = mapOf(
        "mkv" to "video/x-matroska",
        "srt" to "application/x-subrip",
        "log" to "text/plain",
        "md" to "text/markdown",
    )

    fun forExtension(ext: String): String? = EXTRA[ext.lowercase(Locale.ROOT)]

    /** Tipe bersama: sama semua = itu; satu jenis induk = "jenis/*"; selain itu "*/*". */
    fun common(mimes: List<String>): String {
        if (mimes.isEmpty()) return "*/*"
        val first = mimes.first()
        if (mimes.all { it == first }) return first
        val top = first.substringBefore('/')
        return if (mimes.all { it.substringBefore('/') == top }) "$top/*" else "*/*"
    }
}
