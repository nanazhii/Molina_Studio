package com.molina.player.mpv

import android.os.SystemClock
import com.molina.core.common.diag.Diag
import com.molina.player.MPVLib
import com.molina.player.PlaybackLog

/**
 * Pintu tunggal perintah mpv. Tanggung jawab tunggal: menjalankan perintah dan merekamnya.
 * Aturan (ditegakkan gerbang R2): kode di luar paket ini tidak memanggil MPVLib.command secara langsung.
 *
 * Yang tercatat per perintah: nama, jumlah argumen, durasi; untuk perintah bertarget (loadfile, audio-add, ...)
 * ditambah target yang disamarkan (URL: skema+host, berkas: nama saja). Nilai argumen lain TIDAK dicatat.
 * Perintah berfrekuensi tinggi ([QUIET_COMMANDS]) hanya dicatat bila gagal atau lambat.
 * Gateway tidak mengubah thread: pemanggil di jalur UI memakai [MpvThread].
 */
internal object MpvGateway {
    private const val SLOW_MS = 300L
    private val TARGET_COMMANDS = setOf("loadfile", "audio-add", "sub-add", "video-add")
    private val QUIET_COMMANDS = setOf("seek", "frame-step", "frame-back-step", "show-text")

    /** true bila mpv menerima perintah; false bila kosong atau JNI melempar (sudah tercatat). */
    fun command(args: Array<out String>): Boolean {
        val name = args.firstOrNull()
        if (name == null) {
            Diag.bug("mpv", "command-empty", "perintah mpv kosong")
            return false
        }
        val fields = LinkedHashMap<String, String>()
        fields["args"] = (args.size - 1).toString()
        if (name in TARGET_COMMANDS && args.size > 1) fields["target"] = PlaybackLog.target(args[1])
        val t0 = SystemClock.elapsedRealtime()
        try {
            MPVLib.command(args)
        } catch (e: Exception) {
            fields["ms"] = (SystemClock.elapsedRealtime() - t0).toString()
            Diag.bug("mpv", "command-failed", name, e, fields)
            return false
        }
        val ms = SystemClock.elapsedRealtime() - t0
        fields["ms"] = ms.toString()
        if (ms >= SLOW_MS) {
            Diag.bug("mpv", "slow-call", name, null, fields)
        } else if (name !in QUIET_COMMANDS) {
            Diag.behavior("mpv", "command", name, fields)
        }
        return true
    }
}
