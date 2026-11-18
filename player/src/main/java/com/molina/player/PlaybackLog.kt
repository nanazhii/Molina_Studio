package com.molina.player

import android.util.Log
import com.molina.core.common.FailureAction
import com.molina.core.common.StreamError
import com.molina.core.security.UrlPolicy

/**
 * Catatan alur pemutaran di logcat dengan tag seragam "Molina/playback", sehingga mudah disaring
 * (`adb logcat -s Molina/playback Molina/resolver Molina/mpv`). Target selalu disamarkan:
 * URL hanya skema dan host, jalur lokal hanya nama berkas.
 */
internal object PlaybackLog {
    private const val TAG = "Molina/playback"

    fun target(raw: String): String {
        val scheme = UrlPolicy.scheme(raw)
        if (scheme != null && raw.contains("://")) {
            val host = raw.substringAfter("://")
                .substringBefore('/').substringBefore('?').substringBefore('#')
                .substringAfterLast('@')
            return "$scheme://$host"
        }
        return raw.substringBefore('?').substringAfterLast('/').ifEmpty { "(kosong)" }
    }

    fun request(raw: String) {
        Log.i(TAG, "Permintaan putar: ${target(raw)}")
    }

    fun resolved(raw: String, hasAudio: Boolean) {
        Log.i(TAG, "Resolusi berhasil: ${target(raw)} (audio terpisah=$hasAudio)")
    }

    fun failed(error: StreamError, action: FailureAction?) {
        Log.w(TAG, "Resolusi gagal: $error${action?.let { " (aksi=$it)" }.orEmpty()}")
    }

    fun blocked(reason: UrlPolicy.Reason) {
        Log.w(TAG, "Ditolak kebijakan URL: ${reason.name}")
    }
}
