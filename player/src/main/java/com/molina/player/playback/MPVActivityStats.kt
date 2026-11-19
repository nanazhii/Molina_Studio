package com.molina.player.playback

import com.molina.player.mpv.MpvGateway

/**
 * Pengaturan statistik pemutaran: FPS bawaan layar atau halaman skrip stats mpv.
 * Tanggung jawab tunggal: menafsirkan pengaturan dan menyalakan halaman stats sekali pada berkas pertama.
 * Penampilan teks FPS tetap milik layar.
 */
internal class MPVActivityStats {
    /** true bila FPS bawaan diminta. */
    var fpsEnabled = false
        private set
    private var luaPage = 0 // 0 = mati, > 0 = nomor halaman skrip stats
    private var firstFileStarted = false

    /** [statsMode] nilai pengaturan stats_mode: kosong, native_fps, atau lua<N>. */
    fun syncSettings(statsMode: String) {
        fpsEnabled = statsMode == "native_fps"
        luaPage = if (statsMode.startsWith("lua")) statsMode.removePrefix("lua").toIntOrNull() ?: 0 else 0
    }

    /** Dipanggil pada setiap MPV_EVENT_START_FILE; halaman skrip hanya dinyalakan pada berkas pertama. */
    fun onStartFile() {
        if (luaPage > 0 && !firstFileStarted) {
            MpvGateway.command(arrayOf("script-binding", "stats/display-page-$luaPage-toggle"))
        }
        firstFileStarted = true
    }
}
