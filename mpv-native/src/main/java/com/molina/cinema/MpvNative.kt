package com.molina.cinema

/** Pemuat libmpv. Aman dipanggil walau .so belum ada (isLoaded = false). */
object MpvNative {
    val isLoaded: Boolean = try {
        System.loadLibrary("mpv")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }
}
