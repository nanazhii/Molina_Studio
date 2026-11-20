package com.molina.player.stream

import androidx.appcompat.app.AlertDialog
import com.molina.core.common.FailureAction
import com.molina.core.common.StreamSearchItem
import com.molina.core.common.stream.EngineLabel
import com.molina.core.common.stream.YtDlpInstallResult
import com.molina.core.common.stream.YtDlpInstallStage
import com.molina.core.common.stream.YtDlpInstaller
import com.molina.core.common.stream.YtDlpInstallerRegistry
import com.molina.player.mpv.MpvGateway
import com.molina.player.mpv.MpvThread
import com.molina.player.PlaybackLog
import com.molina.player.R
import com.molina.player.StreamLoader
import com.molina.player.search.SearchResultsSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Seluruh alur "dari masukan pengguna sampai `loadfile`": pencarian judul, resolver, pemuatan, trek audio terpisah,
 * dan pesan galat. Satu pintu masuk: [load]. Semua panggilan mpv sinkron berjalan di [MpvThread], bukan main thread.
 * Layar pemutar hanya menyediakan [StreamPlaybackHost]; koordinator tidak menyimpan referensi ke MPVActivity.
 */
internal class StreamPlaybackCoordinator(private val host: StreamPlaybackHost) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mpvThread = MpvThread()
    private var resolveJob: Job? = null
    private var installJob: Job? = null
    @Volatile private var pendingAudioUrl: String? = null

    /**
     * Muat masukan: teks pencarian dicari dulu, halaman situs yang dikenali di-resolve dulu, lainnya diputar apa adanya.
     * [flag] null = pemuatan awal (ditunda sampai surface siap), selain itu flag `loadfile` ([MpvLoadFlag]).
     * [onQueued] dipanggil sekali perintah muat sudah diantrekan.
     */
    fun load(raw: String, flag: String?, onQueued: (() -> Unit)? = null) {
        if (StreamLoader.isSearch(raw)) { // molina-wave3
            searchThenLoad(StreamLoader.searchQuery(raw), flag, onQueued)
            return
        }
        resolveJob?.cancel()
        PlaybackLog.request(raw)
        when {
            StreamLoader.isPageUrl(raw) -> host.showLoading(text(R.string.notice_resolving), null)
            StreamLoader.isRemote(raw) -> host.showLoading(text(R.string.notice_resolving), EngineLabel.of(null))
            else -> host.hideLoading()
        }
        resolveJob = scope.launch {
            val initial = flag == null
            when (val p = withContext(Dispatchers.Default) { StreamLoader.prepare(raw) }) {
                is StreamLoader.Prepared.Direct -> {
                    host.setLoadingSource(EngineLabel.of(null))
                    pendingAudioUrl = null
                    queueLoad(arrayOf("loadfile", p.url, flag ?: MpvLoadFlag.REPLACE), initial, onQueued)
                }
                is StreamLoader.Prepared.Ready -> {
                    PlaybackLog.resolved(p.url, p.audioUrl != null)
                    host.setLoadingSource(EngineLabel.of(p.engine))
                    // Trek audio terpisah menempel pada berkas yang sedang dimuat: tidak bisa di-append.
                    val eff = if (p.audioUrl != null && flag == MpvLoadFlag.APPEND) MpvLoadFlag.REPLACE else (flag ?: MpvLoadFlag.REPLACE)
                    pendingAudioUrl = p.audioUrl
                    queueLoad(StreamLoader.loadfileArgs(p.url, eff, p.headers), initial, onQueued)
                }
                is StreamLoader.Prepared.Failed -> {
                    PlaybackLog.failed(p.error, p.action)
                    val installer = YtDlpInstallerRegistry.get()
                    if (p.action == FailureAction.INSTALL_YTDLP && installer != null) {
                        showInstallPrompt(installer, raw, flag, onQueued, initial) // molina-ytdlp-install
                    } else {
                        showFailure(ResolveMessages.of(host.activity, p.error, p.action), initial)
                    }
                }
                is StreamLoader.Prepared.Blocked -> {
                    PlaybackLog.blocked(p.reason)
                    showFailure(text(R.string.error_resolve_blocked), initial)
                }
            }
        }
    }

    /** Dipanggil saat mpv melaporkan FILE_LOADED: tempelkan trek audio terpisah (stream adaptif) bila ada. */
    fun onFileLoaded() {
        // Dipanggil dari thread event mpv: tampilan hanya boleh disentuh di main thread.
        scope.launch { host.hideLoading() }
        val audio = pendingAudioUrl ?: return
        pendingAudioUrl = null
        mpvThread.post(scope, "audio-add") {
            MpvGateway.command(arrayOf("audio-add", audio, "select", "molina-audio"))
        }
    }

    /** Hentikan pekerjaan yang berjalan dan thread mpv. Panggil dari onDestroy sebelum mpv dihancurkan. */
    fun shutdown() {
        scope.cancel()
        mpvThread.close()
    }

    private fun queueLoad(args: Array<String>, initial: Boolean, onQueued: (() -> Unit)?) {
        mpvThread.post(scope, "loadfile") {
            if (initial) host.loadInitial(args) else MpvGateway.command(args)
        }
        onQueued?.invoke()
    }

    /** Pencarian judul: hasil dipilih lewat dialog, lalu dimuat seperti tautan biasa. Teks pencarian tidak dicatat. */
    private fun searchThenLoad(query: String, flag: String?, onQueued: (() -> Unit)?) {
        resolveJob?.cancel()
        val initial = flag == null
        PlaybackLog.search()
        host.showLoading(text(R.string.notice_searching), null)
        resolveJob = scope.launch {
            when (val s = withContext(Dispatchers.Default) { StreamLoader.search(query) }) {
                is StreamLoader.Searched.Results -> showSearchResults(s.items, flag, onQueued, initial)
                is StreamLoader.Searched.Empty -> showFailure(text(R.string.search_no_results), initial)
                is StreamLoader.Searched.Failed -> showFailure(text(R.string.search_failed), initial)
            }
        }
    }

    private fun showSearchResults(items: List<StreamSearchItem>, flag: String?, onQueued: (() -> Unit)?, initial: Boolean) {
        host.hideLoading()
        if (host.isGone) return
        SearchResultsSheet.show(
            host.activity, items,
            onPick = { load(it.pageUrl, flag, onQueued) },
            onCancel = { if (initial) host.finishCanceled() },
        )
    }

    private fun showFailure(message: String, initial: Boolean) {
        host.hideLoading()
        if (host.isGone) return
        if (!initial) {
            host.showToast(message, true)
            return
        }
        // Belum ada yang diputar: tampilkan dialog lalu tutup, seperti galat putar biasa.
        host.markErrorShown()
        AlertDialog.Builder(host.activity)
            .setTitle(R.string.error_play_title)
            .setMessage(message)
            .setPositiveButton(R.string.dialog_ok) { _, _ -> host.finishCanceled() }
            .setOnCancelListener { host.finishCanceled() }
            .show()
    }

    /**
     * molina-ytdlp-install: yt-dlp belum terpasang. Tawarkan pasang otomatis lewat kontrak [YtDlpInstaller]
     * (pemutar tidak mengenal Debian/PRoot) alih-alih menyuruh pengguna menyalin perintah ke Terminal.
     */
    private fun showInstallPrompt(installer: YtDlpInstaller, raw: String, flag: String?, onQueued: (() -> Unit)?, initial: Boolean) {
        host.hideLoading()
        if (host.isGone) return
        fun decline() {
            if (initial) host.finishCanceled()
        }
        AlertDialog.Builder(host.activity)
            .setTitle(R.string.error_play_title)
            .setMessage(R.string.ytdlp_install_prompt)
            .setPositiveButton(R.string.ytdlp_install_action) { _, _ -> startInstall(installer, raw, flag, onQueued, initial) }
            .setNegativeButton(R.string.ytdlp_install_cancel) { _, _ -> decline() }
            .setOnCancelListener { decline() }
            .show()
    }

    private fun startInstall(installer: YtDlpInstaller, raw: String, flag: String?, onQueued: (() -> Unit)?, initial: Boolean) {
        var userCanceled = false
        val progress = AlertDialog.Builder(host.activity)
            .setTitle(R.string.ytdlp_install_title)
            .setMessage(ResolveMessages.installStage(host.activity, YtDlpInstallStage.PACKAGES))
            .setCancelable(false)
            .setNegativeButton(R.string.ytdlp_install_cancel) { _, _ ->
                userCanceled = true
                installJob?.cancel()
            }
            .show()
        installJob = scope.launch {
            val result = try {
                // onStage datang dari thread latar; tampilan hanya disentuh di main thread.
                installer.install { stage ->
                    scope.launch {
                        if (progress.isShowing) progress.setMessage(ResolveMessages.installStage(host.activity, stage))
                    }
                }
            } catch (e: CancellationException) {
                if (progress.isShowing) progress.dismiss()
                if (userCanceled && initial) host.finishCanceled()
                throw e
            }
            if (progress.isShowing) progress.dismiss()
            if (host.isGone) return@launch
            when (result) {
                is YtDlpInstallResult.Done -> {
                    host.showToast(text(R.string.ytdlp_install_done), true)
                    load(raw, flag, onQueued)
                }
                is YtDlpInstallResult.Failed ->
                    showFailure(ResolveMessages.installFailed(host.activity, result.code), initial)
            }
        }
    }

    private fun text(resId: Int): String = host.activity.getString(resId)
}
