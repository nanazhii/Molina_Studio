package com.molina.player.nowplaying

import android.view.View
import android.view.ViewGroup
import com.molina.player.mpv.MpvProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Layar "Now Playing" mode audio: sampul dimuat di thread IO (hanya berkas lokal), lalu ditampilkan
 * di atas pemutar dengan ruang cadangan untuk bilah kontrol. Tanggung jawab tunggal: sampul dan tata
 * letaknya. Spektrum, banner stasiun, dan kolom ganda landscape adalah fitur terpisah (langkah berikut).
 */
internal class MPVActivityNowPlaying(private val host: MPVActivityNowPlayingHost) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var coverPath: String? = null
    private var job: Job? = null
    private var lastCover: CoverArt.Cover? = null
    private var artLayoutKey = 0L
    private var controlsShown: Boolean? = null

    /** Muat ulang sampul sesuai berkas yang sedang diputar; panggil di thread utama. */
    fun update() {
        val path = if (host.audioUiActive) localPath() else null
        if (path == null) {
            job?.cancel()
            coverPath = null
            show(null)
            return
        }
        if (path == coverPath) return
        coverPath = path
        job?.cancel()
        job = scope.launch {
            val cover = withContext(Dispatchers.IO) { CoverArt.load(path) }
            if (coverPath != path || !host.audioUiActive) return@launch
            show(cover)
            if (cover != null) host.onCoverThumbnail(cover.thumb)
        }
    }

    /** Tinggi kontrol atau ukuran layar berubah (judul 1 vs 2 baris, rotasi): hitung ulang bila kuncinya berbeda. */
    fun relayout() {
        if (!host.audioUiActive) return
        val key = (controlsReserved().toLong() shl 32) or host.artRoot.height.toLong()
        if (key == artLayoutKey) return
        artLayoutKey = key
        show(lastCover)
    }

    /** Selaraskan sampul dengan status tampil/sembunyi bilah kontrol. */
    fun syncLayout() {
        if (!host.audioUiActive) { controlsShown = null; return }
        val shown = host.controlsView.visibility == View.VISIBLE
        if (controlsShown == shown) return
        controlsShown = shown
        show(lastCover)
    }

    fun shutdown() {
        scope.cancel()
    }

    private fun show(cover: CoverArt.Cover?) {
        lastCover = cover
        if (cover == null) {
            host.artRoot.visibility = View.GONE
            host.coverView.setImageDrawable(null)
            host.backdropView.setImageDrawable(null)
            return
        }
        // Sisakan ruang untuk bilah kontrol agar kartu sampul tidak tertimpa.
        val pad = artPadding(controlsReserved())
        host.coverView.setPadding(pad[0], pad[1], pad[2], pad[3])
        host.coverView.setImageBitmap(cover.art)
        host.backdropView.setImageBitmap(cover.backdrop)
        host.artRoot.visibility = View.VISIBLE
    }

    /** Ruang bawah yang dipakai kontrol; kontrol tersembunyi = hanya 24dp, kontrol belum terukur = perkiraan 180dp. */
    private fun controlsReserved(): Int {
        val d = host.artRoot.resources.displayMetrics.density
        val c = host.controlsView
        if (c.visibility != View.VISIBLE) return (24 * d).toInt()
        val bottomMargin = (c.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
        return if (c.height > 0) c.height + bottomMargin else (180 * d).toInt()
    }

    /** Padding (kiri, atas, kanan, bawah) kartu sampul pada mode potret. */
    private fun artPadding(reserved: Int): IntArray {
        val d = host.artRoot.resources.displayMetrics.density
        val side = (32 * d).toInt()
        val top = (24 * d).toInt()
        val bottom = reserved + (12 * d).toInt()
        return intArrayOf(side, top, side, bottom)
    }

    /** Jalur berkas lokal yang sedang diputar; null untuk stream atau berkas yang tidak ada. */
    private fun localPath(): String? {
        val p = MpvProperties.getString("path") ?: return null
        val f = p.removePrefix("file://")
        return if (f.startsWith("/") && File(f).isFile) f else null
    }
}
