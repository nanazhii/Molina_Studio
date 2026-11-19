package com.molina.player.mpv

import android.os.SystemClock
import com.molina.core.common.diag.Diag
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * [ANR-FIX2] Semua get/set property dan command mpv yang sinkron dari jalur UI dijalankan di satu
 * thread khusus, bukan di main thread. Core mpv bisa sibuk (reinit decoder, buka stream HLS,
 * audio-add via jaringan) dan JNI sinkron memblokir pemanggilnya; bila itu main thread, kontrol
 * macet dan Android menampilkan dialog ANR. Satu thread juga menjaga urutan antar-panggilan.
 * Tanggung jawab tunggal: penjadwalan. Pencatatan perintah ada di [MpvGateway].
 */
internal class MpvThread {
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread(r, "molina-mpv-call") }

    val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

    /** Jalankan [block] di thread mpv. Galat dicatat (mpv.call-failed); panggilan lambat (>=300 ms) dicatat (mpv.slow-call). */
    fun post(scope: CoroutineScope, label: String = "", block: () -> Unit) {
        scope.launch(dispatcher) {
            val t0 = SystemClock.elapsedRealtime()
            val name = label.ifEmpty { "-" }
            try {
                block()
            } catch (e: Exception) {
                Diag.bug("mpv", "call-failed", name, e)
            }
            val dt = SystemClock.elapsedRealtime() - t0
            if (dt >= SLOW_MS) {
                Diag.bug("mpv", "slow-call", name, null, mapOf("ms" to dt.toString(), "via" to "thread"))
            }
        }
    }

    /** Hentikan thread; tunggu sebentar agar panggilan yang berjalan selesai sebelum mpv dihancurkan. */
    fun close() {
        executor.shutdown()
        try {
            executor.awaitTermination(1500, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private companion object {
        const val SLOW_MS = 300L
    }
}
