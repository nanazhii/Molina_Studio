package com.molina.player.home

import com.molina.core.common.home.Channel
import com.molina.core.common.home.ChannelQueue
import com.molina.core.security.UrlPolicy
import com.molina.player.Utils

/**
 * Menjadikan saluran terpilih sebuah antrean putar: seluruh daftar mulai dari saluran itu (prev/next di pemutar
 * = ganti saluran). Setiap entri lolos UrlPolicy lebih dulu karena playlist memory:// tidak melewati pemeriksaan
 * per tautan di pemuat stream. Aturan urutan ada di core-common ([ChannelQueue]).
 */
internal object ChannelPlayback {
    fun queue(picked: Channel, all: List<Channel>): List<String> =
        ChannelQueue.urls(all, picked) { UrlPolicy.check(it) is UrlPolicy.Verdict.Allowed }

    /** Target untuk [HomeHost.play]: satu tautan bila antrean tunggal, playlist memory:// bila lebih; null bila kosong. */
    fun target(urls: List<String>): String? = when {
        urls.isEmpty() -> null
        urls.size == 1 -> urls[0]
        else -> runCatching { Utils.makeMemoryPlaylist(urls) }.getOrNull()
    }
}
