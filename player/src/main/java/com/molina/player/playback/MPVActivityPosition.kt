package com.molina.player.playback

import com.molina.core.common.diag.Diag
import com.molina.player.mpv.MpvGateway
import com.molina.player.mpv.MpvProperties

/**
 * Simpan posisi putar (config lanjut-tonton mpv) saat layar ditutup.
 * Tanggung jawab tunggal: memutuskan apakah posisi layak disimpan dan memerintahkan mpv lewat MpvGateway.
 */
internal class MPVActivityPosition {
    /** Nilai pengaturan save_position; diisi layar saat membaca pengaturan. */
    var enabled = false

    fun save() {
        if (!enabled)
            return
        // di akhir berkas (atau status tidak diketahui) tidak ada posisi yang berguna untuk dilanjutkan
        if (MpvProperties.getBoolean("eof-reached") ?: true) {
            Diag.behavior("player", "position-skip-eof", "pemutar di akhir berkas, posisi tidak disimpan")
            return
        }
        MpvGateway.command(arrayOf("write-watch-later-config"))
    }
}
