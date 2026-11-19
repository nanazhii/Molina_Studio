package com.molina.player.stream

import android.content.Context
import com.molina.core.common.FailureAction
import com.molina.core.common.StreamError
import com.molina.player.R

/** Teks galat resolver untuk pengguna. Satu-satunya pemetaan [StreamError]/[FailureAction] ke string. */
internal object ResolveMessages {
    /** Saran perbaikan (pasang yt-dlp, impor cookies) lebih berguna daripada pesan galat umum. */
    fun of(context: Context, error: StreamError, action: FailureAction?): String = when (action) {
        FailureAction.INSTALL_YTDLP -> context.getString(R.string.error_resolve_install_ytdlp)
        FailureAction.IMPORT_COOKIES -> context.getString(R.string.error_resolve_need_cookies)
        else -> of(context, error)
    }

    fun of(context: Context, error: StreamError): String = context.getString(
        when (error) {
            StreamError.NOT_FOUND -> R.string.error_resolve_not_found
            StreamError.PRIVATE -> R.string.error_resolve_private
            StreamError.AGE_RESTRICTED -> R.string.error_resolve_age
            StreamError.GEO_BLOCKED -> R.string.error_resolve_geo
            StreamError.NETWORK -> R.string.error_resolve_network
            else -> R.string.error_resolve_generic
        }
    )
}
