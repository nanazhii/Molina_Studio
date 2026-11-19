package com.molina.player.stream

import androidx.appcompat.app.AppCompatActivity

/**
 * Kontrak antara [StreamPlaybackCoordinator] dan layar pemutar. Koordinator hanya boleh meminta hal-hal di sini,
 * sehingga ia tidak perlu mengenal MPVActivity, binding, atau status pemutar lainnya.
 */
internal interface StreamPlaybackHost {
    /** Konteks untuk string, dialog, dan jendela hasil pencarian. */
    val activity: AppCompatActivity

    /** True bila layar sedang ditutup: koordinator berhenti menampilkan apa pun. */
    val isGone: Boolean

    fun showToast(message: String, cancelPrevious: Boolean)

    /** Muat awal: ditunda sampai surface siap. Dipanggil dari thread mpv. */
    fun loadInitial(args: Array<String>)

    /** Tandai galat sudah dijelaskan ke pengguna, agar dialog galat putar tidak muncul lagi. */
    fun markErrorShown()

    /** Tutup layar dengan hasil dibatalkan. */
    fun finishCanceled()
}
