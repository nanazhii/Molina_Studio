package com.molina.player.audio

import com.molina.player.mpv.MpvProperties

/**
 * Status keluaran audio pemutar: apakah audio benar-benar keluar, dan apakah ini pemutaran audio saja.
 * Tanggung jawab tunggal: membaca property mpv lewat MpvProperties dan menyimpan hasilnya.
 * [update] dipanggil dari thread event mpv, pembacaan dari UI; karena itu [isPlaying] bertanda Volatile.
 */
internal class MPVActivityAudioPresence {
    /** true bila audio benar-benar keluar (termasuk status bisu, bukan status jeda). */
    @Volatile
    var isPlaying = false
        private set

    /** Perbarui dari mpv. Bila belum diketahui ada trek audio aktif, nilai lama dibiarkan agar UI tidak berkedip. */
    fun update() {
        val haveAudio = MpvProperties.getBoolean("current-tracks/audio/selected") ?: return
        isPlaying = haveAudio && MpvProperties.getBoolean("mute") != true
    }

    /** true bila audio keluar dan tidak ada video nyata (kosong atau sampul gambar). */
    fun isAudioOnly(): Boolean {
        if (!isPlaying)
            return false
        val image = MpvProperties.getString("current-tracks/video/image")
        return image.isNullOrEmpty() || image == "yes"
    }
}
