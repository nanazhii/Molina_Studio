// molina-p11
package com.molina.cinema

import android.content.Context
import android.view.Surface

/*
 * [P11] Pengganti sementara untuk kelas yang dulu ada di :mpv-native (dihapus). Cinema tetap terkompilasi,
 * tetapi pemutaran tidak tersedia sampai mesin mpv divendor penuh. Hapus berkas ini saat modul pemutar
 * baru menyediakan MpvPlayback/MpvPlayerFactory (atau ganti panggilannya di CinemaFragment).
 */

/** Permukaan video. attach/detach mengikuti siklus SurfaceHolder; aman dipanggil berulang. */
interface PlayerSurface {
    fun attach(surface: Surface)
    fun resize(width: Int, height: Int)
    fun detach()
}

/** Sesi + permukaan hasil [MpvPlayerFactory.open]. */
class MpvPlayback(val sessionId: String, val session: MolinaPlayerSession, val surface: PlayerSurface)

object MpvPlayerFactory {
    /** Selalu null sampai mesin mpv baru divendor. */
    @Suppress("UNUSED_PARAMETER")
    fun open(context: Context): MpvPlayback? = null
}
