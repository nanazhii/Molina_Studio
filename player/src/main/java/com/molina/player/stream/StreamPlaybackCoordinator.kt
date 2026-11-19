package com.molina.player.stream

import androidx.appcompat.app.AlertDialog
import com.molina.core.common.StreamSearchItem
import com.molina.player.MPVLib
import com.molina.player.MpvThread
import com.molina.player.PlaybackLog
import com.molina.player.R
import com.molina.player.StreamLoader
import com.molina.player.search.SearchResultsSheet
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
        if (StreamLoader.isPageUrl(raw)) host.showToast(text(R.string.notice_resolving), true)
        resolveJob = scope.launch {
            val initial = flag == null
            when (val p = withContext(Dispatchers.Default) { StreamLoader.prepare(raw) }) {
                is StreamLoader.Prepared.Direct -> {
                    pendingAudioUrl = null
                    queueLoad(arrayOf("loadfile", p.url, flag ?: MpvLoadFlag.REPLACE), initial, onQueued)
                }
                is StreamLoader.Prepared.Ready -> {
                    PlaybackLog.resolved(p.url, p.audioUrl != null)
                    // Trek audio terpisah menempel pada berkas yang sedang dimuat: tidak bisa di-append.
                    val eff = if (p.audioUrl != null && flag == MpvLoadFlag.APPEND) MpvLoadFlag.REPLACE else (flag ?: MpvLoadFlag.REPLACE)
                    pendingAudioUrl = p.audioUrl
                    queueLoad(StreamLoader.loadfileArgs(p.url, eff, p.headers), initial, onQueued)
                }
                is StreamLoader.Prepared.Failed -> {
                    PlaybackLog.failed(p.error, p.action)
                    showFailure(ResolveMessages.of(host.activity, p.error, p.action), initial)
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
        val audio = pendingAudioUrl ?: return
        pendingAudioUrl = null
        mpvThread.post(scope, "audio-add") {
            MPVLib.command(arrayOf("audio-add", audio, "select", "molina-audio"))
        }
    }

    /** Hentikan pekerjaan yang berjalan dan thread mpv. Panggil dari onDestroy sebelum mpv dihancurkan. */
    fun shutdown() {
        scope.cancel()
        mpvThread.close()
    }

    private fun queueLoad(args: Array<String>, initial: Boolean, onQueued: (() -> Unit)?) {
        mpvThread.post(scope, "loadfile") {
            if (initial) host.loadInitial(args) else MPVLib.command(args)
        }
        onQueued?.invoke()
    }

    /** Pencarian judul: hasil dipilih lewat dialog, lalu dimuat seperti tautan biasa. Teks pencarian tidak dicatat. */
    private fun searchThenLoad(query: String, flag: String?, onQueued: (() -> Unit)?) {
        resolveJob?.cancel()
        val initial = flag == null
        PlaybackLog.search()
        host.showToast(text(R.string.notice_searching), true)
        resolveJob = scope.launch {
            when (val s = withContext(Dispatchers.Default) { StreamLoader.search(query) }) {
                is StreamLoader.Searched.Results -> showSearchResults(s.items, flag, onQueued, initial)
                is StreamLoader.Searched.Empty -> showFailure(text(R.string.search_no_results), initial)
                is StreamLoader.Searched.Failed -> showFailure(text(R.string.search_failed), initial)
            }
        }
    }

    private fun showSearchResults(items: List<StreamSearchItem>, flag: String?, onQueued: (() -> Unit)?, initial: Boolean) {
        if (host.isGone) return
        SearchResultsSheet.show(
            host.activity, items,
            onPick = { load(it.pageUrl, flag, onQueued) },
            onCancel = { if (initial) host.finishCanceled() },
        )
    }

    private fun showFailure(message: String, initial: Boolean) {
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

    private fun text(resId: Int): String = host.activity.getString(resId)
}
