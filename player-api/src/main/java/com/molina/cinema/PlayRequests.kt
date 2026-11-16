// molina-27m
package com.molina.cinema

/**
 * [molina-27m] Jembatan permintaan putar dari modul Berkas ke Molina Cinema. Berkas memanggil [request];
 * MainActivity memasang [navigator] untuk berpindah ke tab Cinema; CinemaFragment mengambil path lewat [take]
 * setelah sesi mpv siap. Semua dipanggil dari main thread.
 */
object PlayRequests {
    @Volatile private var pending: String? = null

    /** Dipasang MainActivity: pindah ke tab Cinema. */
    @Volatile var navigator: (() -> Unit)? = null

    /** false bila Cinema tidak bisa dibuka (navigator belum terpasang); permintaan tidak disimpan. */
    fun request(path: String): Boolean {
        val go = navigator ?: return false
        pending = path
        go()
        return true
    }

    /** Ambil lalu kosongkan permintaan yang menunggu (sekali pakai). */
    fun take(): String? {
        val p = pending
        pending = null
        return p
    }
}
