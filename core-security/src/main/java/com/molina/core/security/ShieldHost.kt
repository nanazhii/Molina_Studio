package com.molina.core.security

/** Kode galat pemutar untuk pemuatan yang diblokir Shield (dipakai mesin pemutar dan UI Cinema). */
const val SHIELD_BLOCKED_CODE = -403

/**
 * Label host untuk pesan di layar. Hanya host: skema, userinfo (kredensial), port, path, dan query
 * dibuang. Null bila URL tidak terbaca atau host memuat karakter kontrol/spasi.
 */
object ShieldHost {
    private const val MAX = 63

    fun label(uri: String): String? {
        val t = uri.trim()
        val scheme = UrlPolicy.scheme(t) ?: return null
        if (t.length < scheme.length + 3) return null
        val authority = t.substring(scheme.length + 3)
            .takeWhile { it != '/' && it != '?' && it != '#' }
            .substringAfterLast('@')
        val host = parseHostPort(authority, 1)?.first ?: return null
        if (host.any { it.isISOControl() || it.isWhitespace() }) return null
        return host.lowercase().take(MAX)
    }
}
