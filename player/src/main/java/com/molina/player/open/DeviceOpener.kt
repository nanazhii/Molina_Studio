package com.molina.player.open

import android.widget.Toast
import com.molina.core.common.diag.Diag
import com.molina.core.common.picker.LocalPickerRegistry
import com.molina.core.common.picker.PickKind
import com.molina.player.R
import java.io.File

/**
 * "Buka dari perangkat": pilihan Berkas, Folder, atau Folder beserta subfolder, lalu pemilih lokal Molina
 * (kontrak [com.molina.core.common.picker.LocalPicker]). Tidak menyentuh mpv; memutar lewat [OpenHost].
 */
internal class DeviceOpener(private val host: OpenHost) {
    private var lastDir: File? = null

    /** Tampilkan dialog pilihan. */
    fun start() {
        OpenChooserSheet.show(host.hostContext()) { choice ->
            when (choice) {
                OpenChooserSheet.Choice.FILE -> pickFile()
                OpenChooserSheet.Choice.FOLDER -> pickFolder(deep = false)
                OpenChooserSheet.Choice.FOLDER_DEEP -> pickFolder(deep = true)
            }
        }
    }

    /** Langsung ke pemilih berkas (dipakai pemulihan "Ingat pilihan"). */
    fun pickFile() = launch(PickKind.FILE) { file ->
        lastDir = file.parentFile
        host.playPath(file.absolutePath)
    }

    private fun pickFolder(deep: Boolean) = launch(PickKind.FOLDER) { dir ->
        lastDir = dir
        Diag.behavior("player", "open-local-folder", "folder dipilih", mapOf("deep" to deep.toString()))
        host.playFolder(dir, deep)
    }

    private fun launch(kind: PickKind, onPicked: (File) -> Unit) {
        val ctx = host.hostContext()
        val picker = LocalPickerRegistry.get()
        if (picker == null) {
            Diag.bug("player", "open-picker-missing", "pemilih lokal belum terpasang")
            Toast.makeText(ctx, R.string.chooser_no_picker, Toast.LENGTH_SHORT).show()
            return
        }
        host.onPickerOpened()
        Diag.behavior("player", "open-local", "pemilih dibuka", mapOf("kind" to kind.name.lowercase()))
        picker.pick(ctx, kind, lastDir) { file -> if (file != null) onPicked(file) }
    }
}
