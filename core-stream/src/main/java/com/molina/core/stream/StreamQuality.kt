package com.molina.core.stream

import java.util.concurrent.ConcurrentHashMap

/** Mode kualitas untuk resolver. Tidak ada pemilih resolusi manual: AUTO (adaptif) atau AUDIO (hanya suara). */
enum class VideoQuality(val label: String, val maxHeight: Int) {
    AUTO("Otomatis", 0),
    /** Batas internal AUTO di jaringan non-Wi-Fi (dipakai StreamPicker); bukan pilihan pengguna. */
    P720("720p", 720),
    AUDIO("Hanya audio", 0);

    /** Tinggi tersedia terbaik: tertinggi <= batas; bila semuanya di atas batas, yang terendah. Null bila kosong. */
    fun chooseHeight(heights: Collection<Int>): Int? {
        val h = heights.filter { it > 0 }.distinct().sorted()
        if (h.isEmpty()) return null
        if (maxHeight <= 0) return h.last()
        return h.lastOrNull { it <= maxHeight } ?: h.first()
    }
}

/** Mode aktif + peta URL video -> URL audio terpisah (YouTube 720p ke atas = video-only + audio). */
object StreamQuality {
    @Volatile var current: VideoQuality = VideoQuality.AUTO
    /** Tinggi hasil resolve terakhir (0 = audio/tidak diketahui), diisi resolver. */
    @Volatile var lastHeight: Int = 0
    @Volatile var lastFallback: Boolean = false
    private val audio = ConcurrentHashMap<String, String>()

    fun set(v: VideoQuality) { current = v }

    fun rememberAudio(videoUrl: String, audioUrl: String) {
        if (audio.size >= 64) audio.clear()
        audio[videoUrl] = audioUrl
    }

    fun audioFor(videoUrl: String?): String? = videoUrl?.let { audio[it] }
}
