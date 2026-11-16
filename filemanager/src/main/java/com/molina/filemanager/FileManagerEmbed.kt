package com.molina.filemanager

import android.content.Context
import android.view.View
import kotlinx.coroutines.CoroutineScope

/** [molina-27b] Pintu masuk publik: file manager salinan drawer Terminal, mode tertanam (tanpa nano/Jalankan/cd). */
object FileManagerEmbed {
    /** [molina-27d] Pegangan: [view] untuk dipasang, [refresh] untuk memuat ulang tree dari luar. */
    class Handle internal constructor(
        val view: View,
        private val onRefresh: () -> Unit,
        private val onFinished: (Boolean) -> Unit, // molina-27e
        private val onCancelSelection: () -> Boolean,
        private val onArchiveClip: () -> Unit, // molina-27f
        private val onOpenDir: (java.io.File) -> Unit, // molina-27g0
        private val onNavigateUp: () -> Boolean, // molina-27u
    ) {
        /** [molina-27u] Back sistem: tutup mode pilih/pencarian, jika tidak ada naik satu folder; true bila ditangani. */
        fun navigateUp(): Boolean = onNavigateUp()

        /** [molina-27f] Klip "Ekstrak ke..." berubah (dari lembar Lihat isi): bilah klip digambar ulang. */
        fun archiveClipChanged() = onArchiveClip()

        /** [molina-27g0] Buka [dir] di tree: leluhur dibentangkan dan baris digulir ke sana; di luar folder utama, jadi folder utama. */
        fun openDirectory(dir: java.io.File) = onOpenDir(dir)

        fun refresh() = onRefresh()

        /** [molina-27e] Salin/pindah selesai: segarkan tree; [clearClip] true mengosongkan papan klip. */
        fun transferFinished(clearClip: Boolean) = onFinished(clearClip)

        /** [molina-27e] Tutup mode pilih bila aktif; true bila ada yang ditutup (untuk tombol Back). */
        fun cancelSelection(): Boolean = onCancelSelection()
    }

    fun create(
        context: Context,
        scope: CoroutineScope,
        actions: FileManagerActions = FileManagerActions.None,
        onSelectionChanged: (Boolean) -> Unit = {}, // molina-27e: true saat mode pilih aktif (untuk mengaktifkan Back)
    ): Handle {
        val v = FileDrawerView(
            context, FileDrawerState(), scope, NoopHost,
            embedded = true, actions = actions, onSelectionChanged = onSelectionChanged,
        )
        return Handle(v, { v.refresh() }, { v.onTransferFinished(it) }, { v.cancelSelection() }, { v.onArchiveClipChanged() }, { v.openDirectory(it) }, { v.navigateUp() })
    }

    private object NoopHost : FileDrawerView.Host {
        override fun send(text: String) = Unit
        override fun hasSession(): Boolean = false
        override fun isProotSession(): Boolean = false
        override fun isFullScreenProgram(): Boolean = false
        override fun runInNewSession(line: (proot: Boolean) -> String?): Boolean = false
        override fun activateSessionAt(guestDir: String): Boolean = false
        override fun changeDirectory(shellPath: String) = Unit
        override fun showNanoKeys() = Unit
        override fun autoRunNano() = Unit
        override fun runMode(): RunMode = RunMode.ASK
        override fun rememberRunMode(mode: RunMode) = Unit
        override fun runLogEnabled(): Boolean = false
        override fun onRootChanged(path: String) = Unit
        override fun onOpenChanged(open: Boolean) = Unit
    }
}
