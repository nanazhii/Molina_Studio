// molina-patch10b
package com.molina.terminal

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import com.molina.log.MolinaEvents
import com.molina.terminal.proot.DebianLayout
import com.molina.terminal.proot.InstallCancelledException
import com.molina.terminal.proot.InstallProgress
import com.molina.terminal.proot.ProotPin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal enum class SetupStage { DOWNLOAD, VERIFY, EXTRACT }

internal sealed interface SetupState {
    object Hidden : SetupState
    object Offer : SetupState
    class Installing(val stage: SetupStage, val done: Long, val total: Long) : SetupState
    class Failed(val message: String) : SetupState
    class NeedsProot(val problems: List<String>) : SetupState
    class Dismissed(val rootfsMissing: Boolean) : SetupState
}

/**
 * Orkestrasi pemasangan Debian, di-scope ke Activity (bertahan saat pindah tab/rotasi).
 * [listener] selalu dipanggil di main thread.
 */
internal class DebianSetupViewModel(app: Application) : AndroidViewModel(app) {

    private val layout = DebianLayout(app.filesDir)
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var cancelRequested = false
    @Volatile private var installing = false
    @Volatile private var dismissed = false

    var state: SetupState = SetupState.Hidden
        private set

    var listener: ((SetupState) -> Unit)? = null

    /** Apakah sesi terminal yang sedang berjalan sudah berupa Debian. */
    var sessionIsDebian = false

    fun isReady(): Boolean = layout.isReady()

    fun refresh() {
        if (installing) return
        scope.launch { post(evaluate()) }
    }

    fun install() {
        if (installing) return
        installing = true
        cancelRequested = false
        MolinaEvents.behavior("proot", "install.start")
        scope.launch {
            try {
                layout.installer { cancelRequested }.install { p -> post(progressState(p)) }
                ensureProot()
                MolinaEvents.behavior("proot", "install.done")
                post(evaluate())
            } catch (e: InstallCancelledException) {
                MolinaEvents.behavior("proot", "install.cancelled")
                post(evaluate())
            } catch (e: Exception) {
                MolinaEvents.bug("proot", "install-failed", e.message ?: e.javaClass.simpleName, e)
                post(SetupState.Failed(e.message ?: e.javaClass.simpleName))
            } finally {
                installing = false
            }
        }
    }

    /** Pastikan proot siap: impor manual bila ada, kalau tidak unduh dari Release yang dipin. */
    private fun ensureProot() {
        if (layout.isProotReady()) return
        runCatching { layout.importProot() }
        if (layout.isProotReady() || !ProotPin.CONFIGURED) return
        post(SetupState.Installing(SetupStage.VERIFY, 0, 0))
        layout.prootInstaller { cancelRequested }.install()
        MolinaEvents.behavior("proot", "proot.downloaded")
    }

    fun cancel() {
        cancelRequested = true
    }

    fun dismiss() {
        dismissed = true
        refresh()
    }

    fun reopen() {
        dismissed = false
        refresh()
    }

    private fun evaluate(): SetupState {
        if (layout.isReady()) return SetupState.Hidden
        if (!layout.isRootfsInstalled()) {
            return if (dismissed) SetupState.Dismissed(true) else SetupState.Offer
        }
        val problems = try {
            layout.importProot().problems
        } catch (e: Exception) {
            listOf(e.message ?: "gagal menyalin proot")
        }
        if (layout.isReady()) return SetupState.Hidden
        if (dismissed) return SetupState.Dismissed(false)
        // Pin Release terisi: tawarkan unduhan otomatis; kartu manual hanya bila pin belum ada.
        return if (ProotPin.CONFIGURED) SetupState.Offer else SetupState.NeedsProot(problems)
    }

    private fun progressState(p: InstallProgress): SetupState = when (p) {
        is InstallProgress.Downloading -> SetupState.Installing(SetupStage.DOWNLOAD, p.bytes, p.total)
        InstallProgress.Verifying -> SetupState.Installing(SetupStage.VERIFY, 0, 0)
        is InstallProgress.Extracting -> SetupState.Installing(SetupStage.EXTRACT, p.done.toLong(), p.total.toLong())
        InstallProgress.Done -> SetupState.Installing(SetupStage.EXTRACT, 1, 1)
    }

    private fun post(s: SetupState) {
        main.post {
            state = s
            listener?.invoke(s)
        }
    }

    override fun onCleared() {
        cancelRequested = true
        listener = null
        scope.cancel()
    }
}
