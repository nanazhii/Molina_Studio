package com.molina.core.common.stream

/**
 * Nama mesin yang memutuskan sebuah tautan, untuk chip di kartu pemuatan. Pemetaan id resolver ke teks hanya ada di sini.
 * Murni, tanpa Android. Id berasal dari [com.molina.core.common.StreamResolver.id] (diisi rantai ke ResolvedStream.engine).
 */
object EngineLabel {
    private const val MAX = 20

    /** Null atau "direct" berarti tidak ada resolver: mpv memutar tautan apa adanya. */
    fun of(engineId: String?): String {
        val id = engineId?.trim()?.lowercase().orEmpty()
        return when (id) {
            "", "direct" -> "Langsung"
            "newpipe" -> "NewPipe"
            "ytdlp", "yt-dlp" -> "yt-dlp"
            "generic" -> "Web"
            else -> id.take(MAX)
        }
    }
}
