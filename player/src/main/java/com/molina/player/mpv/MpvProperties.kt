package com.molina.player.mpv

import android.os.SystemClock
import com.molina.core.common.diag.Diag
import com.molina.player.MPVLib

/**
 * Pintu tunggal property dan opsi mpv (pendamping [MpvGateway] yang memegang perintah).
 * Tanggung jawab tunggal: menjalankan get/set/option/observe dan merekamnya. Aturan gerbang R2: kode di luar
 * paket ini tidak memanggil MPVLib.getProperty*, setProperty*, setOption*, observeProperty secara langsung.
 *
 * Kebijakan catatan (logcat tetap terbaca, tidak banjir):
 *  - get: tidak dicatat bila berhasil (nilai null itu wajar, mis. belum ada berkas); dicatat bila gagal atau lambat.
 *  - set: dicatat sebagai mpv.property-set dengan NAMA property. Nilai hanya dicatat untuk nama di [SAFE_VALUES].
 *    Property berfrekuensi tinggi ([QUIET]) hanya dicatat bila gagal atau lambat.
 *  - option: dicatat hanya bila ditolak mpv (kode < 0): mpv.option-rejected dengan nama dan kode, tanpa nilai.
 * Gateway tidak mengubah thread: pemanggil di jalur UI memakai [MpvThread].
 */
internal object MpvProperties {
    private const val SLOW_MS = 300L
    private val QUIET = setOf("time-pos", "volume", "brightness", "contrast", "gamma", "saturation", "hue", "sub-pos", "panscan")
    private val SAFE_VALUES = setOf(
        "vo", "hwdec", "pause", "speed", "loop-file", "loop-playlist", "keep-open", "shuffle",
        "video-aspect-override", "force-window", "android-surface-size",
    )

    fun getInt(name: String): Int? = guarded("get", name) { MPVLib.getPropertyInt(name) }
    fun getDouble(name: String): Double? = guarded("get", name) { MPVLib.getPropertyDouble(name) }
    fun getBoolean(name: String): Boolean? = guarded("get", name) { MPVLib.getPropertyBoolean(name) }
    fun getString(name: String): String? = guarded("get", name) { MPVLib.getPropertyString(name) }

    fun setInt(name: String, value: Int) = write(name, value.toString()) { MPVLib.setPropertyInt(name, value) }
    fun setDouble(name: String, value: Double) = write(name, value.toString()) { MPVLib.setPropertyDouble(name, value) }
    fun setBoolean(name: String, value: Boolean) = write(name, value.toString()) { MPVLib.setPropertyBoolean(name, value) }
    fun setString(name: String, value: String) = write(name, value) { MPVLib.setPropertyString(name, value) }

    /** Kode mpv (>= 0 berhasil). -1 bila JNI melempar (sudah tercatat sebagai mpv.property-failed). */
    fun setOption(name: String, value: String): Int {
        val rc = guarded("option", name) { MPVLib.setOptionString(name, value) } ?: return -1
        if (rc < 0) Diag.bug("mpv", "option-rejected", name, null, mapOf("rc" to rc.toString()))
        return rc
    }

    fun observe(name: String, format: Int) {
        guarded("observe", name) { MPVLib.observeProperty(name, format) }
    }

    private inline fun write(name: String, value: String, block: () -> Unit) {
        if (guarded("set", name, block) == null) return
        if (name in QUIET) return
        val fields = LinkedHashMap<String, String>()
        if (name in SAFE_VALUES) fields["value"] = value
        Diag.behavior("mpv", "property-set", name, fields)
    }

    /** Jalankan [block]; null bila JNI melempar (tercatat). Panggilan >= 300 ms tercatat sebagai mpv.slow-call. */
    private inline fun <T> guarded(op: String, name: String, block: () -> T): T? {
        val t0 = SystemClock.elapsedRealtime()
        val out = try {
            block()
        } catch (e: Exception) {
            Diag.bug("mpv", "property-failed", name, e, mapOf("op" to op, "ms" to (SystemClock.elapsedRealtime() - t0).toString()))
            return null
        }
        val ms = SystemClock.elapsedRealtime() - t0
        if (ms >= SLOW_MS) Diag.bug("mpv", "slow-call", name, null, mapOf("op" to op, "ms" to ms.toString()))
        return out
    }
}
