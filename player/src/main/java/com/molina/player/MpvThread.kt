package com.molina.player

import android.os.SystemClock
import android.util.Log
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
 */
internal class MpvThread {
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread(r, "molina-mpv-call") }

    val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

    /** Jalankan [block] di thread mpv. Galat ditelan dan dicatat; panggilan lambat (>=300 ms) dilaporkan. */
    fun post(scope: CoroutineScope, label: String = "", block: () -> Unit) {
        scope.launch(dispatcher) {
            val t0 = SystemClock.elapsedRealtime()
            try {
                block()
            } catch (e: Exception) {
                Log.w(TAG, "panggilan gagal (${label.ifEmpty { "-" }}): ${e.message}")
            }
            val dt = SystemClock.elapsedRealtime() - t0
            if (dt >= SLOW_MS) Log.w(TAG, "[mpv-call] ${label.ifEmpty { "panggilan" }} lambat $dt ms (UI tetap responsif)")
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
        const val TAG = "Molina/mpv"
        const val SLOW_MS = 300L
    }
}
