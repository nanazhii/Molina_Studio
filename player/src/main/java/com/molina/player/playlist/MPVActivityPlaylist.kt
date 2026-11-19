package com.molina.player.playlist

import android.app.Activity
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.molina.player.FilePickerActivity
import com.molina.player.MPVView
import com.molina.player.PlaylistDialog
import com.molina.player.R
import com.molina.player.StateRestoreCallback
import com.molina.player.StreamLoader
import com.molina.player.Utils
import com.molina.player.mpv.MpvGateway
import com.molina.player.mpv.MpvProperties

/**
 * Daftar putar mpv dan tambah trek eksternal yang dulu tersebar di MPVActivity.
 * Tanggung jawab tunggal: perintah daftar putar ke mpv (lewat MpvGateway/MpvProperties) dan dialog daftar putar.
 * Hal milik layar (pemilih berkas, antrean stream) datang lewat [MPVActivityPlaylistHost].
 */
internal class MPVActivityPlaylist(
    private val activity: AppCompatActivity,
    private val playerProvider: () -> MPVView,
    private val host: MPVActivityPlaylistHost,
) {
    private val player get() = playerProvider()

    /**
     * Seperti pemutar pada umumnya: bila berkas sudah berjalan lebih dari [RESTART_AFTER_S] detik, atau ini item
     * pertama, mulai ulang berkas ini; selain itu pindah ke item sebelumnya.
     */
    fun previous() {
        val elapsed = MpvProperties.getDouble("time-pos") ?: 0.0
        val atFirst = (MpvProperties.getInt("playlist-pos") ?: 0) <= 0
        if (elapsed > RESTART_AFTER_S || atFirst) {
            MpvGateway.command(arrayOf("seek", "0", "absolute"))
        } else {
            MpvGateway.command(arrayOf("playlist-prev"))
        }
    }

    fun next() {
        MpvGateway.command(arrayOf("playlist-next"))
    }

    /** [cmd] = audio-add atau sub-add; [path] sudah berupa jalur berkas atau URL yang bisa dibuka mpv. */
    fun addExternalTrack(cmd: String, path: String) {
        MpvGateway.command(arrayOf(cmd, path, "cached"))
    }

    /** Jalur berkas yang sedang diputar; dipakai sebagai folder awal pemilih berkas. */
    fun playingPath(): String = MpvProperties.getString("path") ?: ""

    fun openPlaylistMenu(restore: StateRestoreCallback) {
        val impl = PlaylistDialog(player)
        lateinit var dialog: AlertDialog

        impl.listeners = object : PlaylistDialog.Listeners {
            private fun openFilePicker(skip: Int) {
                host.pickPlaylistFile(skip) { result, data ->
                    if (result == Activity.RESULT_OK) {
                        val path = data!!.getStringExtra("path")!!
                        if (StreamLoader.isSearch(path)) { // molina-wave3
                            host.loadStream(path) { impl.refresh() }
                        } else {
                            MpvGateway.command(arrayOf("loadfile", path, "append"))
                            impl.refresh()
                        }
                    }
                }
            }
            override fun pickFile() = openFilePicker(FilePickerActivity.FILE_PICKER)

            override fun openUrl() {
                val helper = Utils.OpenUrlDialog(activity)
                with (helper) {
                    builder.setPositiveButton(R.string.dialog_ok) { _, _ ->
                        if (StreamLoader.isSearch(helper.text)) { // molina-wave3
                            host.loadStream(helper.text) { impl.refresh() }
                        } else {
                            MpvGateway.command(arrayOf("loadfile", helper.text, "append"))
                            impl.refresh()
                        }
                    }
                    builder.setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.cancel() }
                    create().show()
                }
            }

            override fun onItemPicked(item: MPVView.PlaylistItem) {
                MpvProperties.setInt("playlist-pos", item.index)
                dialog.dismiss()
            }

            override fun onExit() = dialog.dismiss()

            override fun onMenu() {
                dialog.dismiss()
                host.openTopMenu()
            }
        }

        dialog = with (AlertDialog.Builder(activity, R.style.MolinaPlayerTheme_PlayerDialog)) {
            val inflater = LayoutInflater.from(context)
            setView(impl.buildView(inflater))
            setOnDismissListener { restore() }
            create()
        }
        dialog.show()
    }

    private companion object {
        const val RESTART_AFTER_S = 3.0
    }
}
