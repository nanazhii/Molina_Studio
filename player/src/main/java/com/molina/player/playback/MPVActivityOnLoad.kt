package com.molina.player.playback

import com.molina.player.mpv.MpvGateway

/**
 * Antrean perintah mpv yang dijalankan saat berkas mulai dibuka (opsi per berkas dan subtitle dari intent).
 * Tanggung jawab tunggal: menampung dan menjalankan antrean lewat MpvGateway.
 */
internal class MPVActivityOnLoad {
    private val commands = mutableListOf<Array<String>>()

    fun clear() {
        commands.clear()
    }

    fun add(args: Array<String>) {
        commands.add(args)
    }

    /** Jalankan semua perintah lalu kosongkan antrean. */
    fun flush() {
        val pending = commands.toTypedArray()
        commands.clear()
        for (c in pending)
            MpvGateway.command(c)
    }
}
