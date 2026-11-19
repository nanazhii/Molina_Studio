package com.molina.player.playlist

import android.content.Intent

/**
 * Kontrak MPVActivityPlaylist ke layar pemutar. Hanya hal yang memang milik Activity:
 * memulai pemilih berkas (hasilnya kembali lewat onActivityResult) dan antrean pemuatan stream.
 */
internal interface MPVActivityPlaylistHost {
    /** Buka pemilih berkas daftar putar; [callback] menerima (resultCode, data). */
    fun pickPlaylistFile(skip: Int, callback: (Int, Intent?) -> Unit)

    /** Tambah ke daftar putar lewat jalur stream (pencarian judul, resolver); [onQueued] dipanggil setelah antre. */
    fun loadStream(path: String, onQueued: () -> Unit)
}
