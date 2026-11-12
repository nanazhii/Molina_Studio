package com.molina.cinema

/**
 * Pemuat pustaka native mpv (libmpv + libplayer, JNI MPVLib). Aman dipanggil walau .so belum ada
 * (isLoaded = false). Pemuatan di sini hanya memeriksa ketersediaan; MPVLib memuat ulang dengan aman
 * karena System.loadLibrary idempoten untuk pustaka yang sudah termuat.
 */
object MpvNative {
    val isLoaded: Boolean = try {
        System.loadLibrary("mpv")
        System.loadLibrary("player")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }
}
