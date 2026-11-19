package com.molina.core.common.home

/** Satu saluran TV dari daftar M3U. [group] kosong bila daftar tidak mengelompokkan. */
data class Channel(val name: String, val url: String, val group: String = "")

/**
 * Pengurai M3U/M3U8 murni (tanpa Android, tanpa jaringan). Hanya tautan http(s) yang diterima:
 * jalur berkas, file://, concat: dan skema lain dibuang karena daftar bisa berasal dari luar.
 * Kebijakan URL penuh (UrlPolicy) tetap diterapkan oleh pemutar saat saluran dibuka.
 */
object M3uParser {
    const val DEFAULT_MAX_ENTRIES = 5000
    private const val MAX_TEXT = 200
    private val GROUP = Regex("group-title=\"([^\"]*)\"", RegexOption.IGNORE_CASE)

    fun parse(text: String, maxEntries: Int = DEFAULT_MAX_ENTRIES): List<Channel> {
        val out = ArrayList<Channel>()
        var name: String? = null
        var group = ""
        var extGroup = ""
        for (raw in text.removePrefix("\uFEFF").lineSequence()) {
            if (out.size >= maxEntries) break
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    name = titleOf(line)
                    group = GROUP.find(line)?.groupValues?.get(1)?.trim().orEmpty()
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> extGroup = line.substringAfter(':').trim()
                line.startsWith("#") -> Unit
                else -> {
                    if (isHttp(line)) {
                        val label = name?.takeIf { it.isNotBlank() } ?: hostOf(line)
                        out += Channel(label.take(MAX_TEXT), line, group.ifEmpty { extGroup }.take(MAX_TEXT))
                    }
                    name = null
                    group = ""
                    extGroup = ""
                }
            }
        }
        return out
    }

    /** Judul = teks setelah koma pertama di LUAR tanda kutip (atribut boleh memuat koma). */
    private fun titleOf(extinf: String): String {
        var inQuote = false
        for ((i, c) in extinf.withIndex()) {
            if (c == '"') inQuote = !inQuote
            else if (c == ',' && !inQuote) return extinf.substring(i + 1).trim()
        }
        return ""
    }

    private fun isHttp(s: String): Boolean =
        s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true)

    private fun hostOf(url: String): String =
        url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
}

/** Penyaring daftar saluran untuk kolom cari: semua kata harus cocok di nama atau grup (tanpa beda huruf). */
object ChannelFilter {
    fun apply(all: List<Channel>, query: String): List<Channel> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return all
        return all.filter { c ->
            val hay = (c.name + " " + c.group).lowercase()
            words.all { hay.contains(it) }
        }
    }
}
