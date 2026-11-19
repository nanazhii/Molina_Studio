package com.molina.player.nowplaying

import android.graphics.Bitmap
import android.view.View
import android.widget.ImageView

/**
 * Kontrak MPVActivityNowPlaying ke layar pemutar. Hanya hal yang memang milik Activity:
 * tiga tampilan layar sampul, bilah kontrol (untuk ruang yang dicadangkan), status mode audio,
 * dan penerusan thumbnail ke notifikasi/sesi media.
 */
internal interface MPVActivityNowPlayingHost {
    /** Wadah layar sampul (latar + kartu); GONE bila tidak ada sampul. */
    val artRoot: View
    val coverView: ImageView
    val backdropView: ImageView

    /** Bilah kontrol bawah; tingginya menentukan ruang yang disisakan agar kartu sampul tidak tertimpa. */
    val controlsView: View

    /** true bila layar sedang memakai UI audio (hanya saat itu sampul ditampilkan). */
    val audioUiActive: Boolean

    /** Sampul trek ini siap: dipakai notifikasi dan sesi media menggantikan bingkai yang di-grab mpv. */
    fun onCoverThumbnail(thumb: Bitmap)
}
