package com.molina.player.home

import android.content.Context

/**
 * Kontrak Home ke layar: satu-satunya hal yang diketahui paket `home` tentang layar yang
 * menampungnya (MainScreenFragment). Paket ini tidak menyentuh MPVActivity maupun mpv.
 */
interface HomeHost {
    /** Context untuk dialog dan toast (milik layar yang sedang tampil). */
    fun hostContext(): Context

    /** Buka pemilih berkas/folder dari perangkat. */
    fun openFromDevice()

    /** Buka layar Setelan mpv. */
    fun openSettings()

    /** Putar tautan atau jalur berkas lewat alur pemutaran yang sudah ada. */
    fun play(target: String)
}

/** Isi menu Home. Menambah menu = satu nilai di sini, satu baris di [HomeMenu], satu cabang di [HomeController]. */
internal enum class HomeAction { FILES, CHANNELS, SETTINGS, REFRESH }
