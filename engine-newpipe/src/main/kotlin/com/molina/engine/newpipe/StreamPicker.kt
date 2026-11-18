package com.molina.engine.newpipe

import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

/** Satu URL siap putar dari [StreamInfo]. [kind] hanya untuk log. [audioUrl] != null = trek audio terpisah. */
internal data class Pick(val url: String, val kind: String, val height: Int = 0, val audioUrl: String? = null)

/**
 * Memilih URL yang bisa langsung dimuat mpv. Urutan:
 * live -> HLS; adaptif (video-only H.264 <= [maxHeight], tanpa AV1, + audio asli terbaik);
 * video progresif ber-audio tertinggi; audio progresif; terakhir HLS.
 * Fungsi murni terhadap [StreamInfo], tanpa jaringan.
 */
internal object StreamPicker {

    fun pick(info: StreamInfo, maxHeight: Int): Pick? {
        val hls = info.hlsUrl
        val live = info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM
        if (live && !hls.isNullOrBlank()) return Pick(hls, "hls-live")

        val audio = bestAudio(info.audioStreams.orEmpty())
        pickAdaptive(info, maxHeight, audio)?.let { return it }

        bestMuxed(info.videoStreams.orEmpty())?.let { v -> return Pick(v.content, "progresif", v.height) }
        audio?.let { return Pick(it.content, "audio") }
        if (!hls.isNullOrBlank()) return Pick(hls, "hls")
        return null
    }

    private fun pickAdaptive(info: StreamInfo, maxHeight: Int, audio: AudioStream?): Pick? {
        if (audio == null) return null
        val cands = info.videoOnlyStreams.orEmpty().filter {
            it.isUrl() && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.height > 0 &&
                it.codec?.startsWith("av01", ignoreCase = true) != true
        }
        val avc = cands.filter { it.codec?.startsWith("avc", ignoreCase = true) == true }
        val pool = avc.ifEmpty { cands }
        val within = pool.filter { it.height <= maxHeight }
        val h = within.maxOfOrNull { it.height } ?: pool.minOfOrNull { it.height } ?: return null
        val v = pool.filter { it.height == h }.maxByOrNull { it.bitrate } ?: return null
        return Pick(v.content, "adaptif", v.height, audio.content)
    }

    private fun bestMuxed(list: List<VideoStream>): VideoStream? =
        list.asSequence()
            .filter { it.isUrl() && !it.isVideoOnly() && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .maxWithOrNull(compareBy<VideoStream>({ it.height }, { it.bitrate }))

    private fun isOriginal(a: AudioStream): Boolean = a.audioTrackType?.name == "ORIGINAL"

    /** Audio terbaik dari trek asli (bukan trek dubbing acak). */
    private fun bestAudio(list: List<AudioStream>): AudioStream? {
        val usable = list.filter { it.isUrl() && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        if (usable.isEmpty()) return null
        val pool = usable.filter { isOriginal(it) }
            .ifEmpty { usable.filter { it.audioTrackId.isNullOrEmpty() } }
            .ifEmpty { usable }
        return pool.maxByOrNull { it.averageBitrate }
    }

    /**
     * Perkiraan kedaluwarsa (epoch ms) dari parameter `expire` (detik) pada query atau segmen
     * path `/expire/<detik>`. Null bila tidak ditemukan atau tidak masuk akal.
     */
    fun expiresAtMs(url: String): Long? {
        val m = EXPIRE.find(url) ?: return null
        val sec = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toLongOrNull() ?: return null
        return if (sec in 1_000_000_000L..99_999_999_999L) sec * 1000 else null
    }

    private val EXPIRE = Regex("[?&]expire=(\\d{9,11})|/expire/(\\d{9,11})")
}
