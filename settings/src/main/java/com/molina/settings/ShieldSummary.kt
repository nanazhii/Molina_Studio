package com.molina.settings

import com.molina.core.security.ShieldSnapshot

/** Teks ringkasan Shield untuk layar Keamanan. Murni agar mudah diuji. */
object ShieldSummary {
    enum class Level { ACTIVE, WARN }

    /** WARN bila Shield dimatikan atau fail-closed; dipakai untuk warna kartu (teal vs aksen peringatan). */
    fun level(s: ShieldSnapshot, failClosed: Boolean): Level = if (!s.enabled || failClosed) Level.WARN else Level.ACTIVE

    fun text(s: ShieldSnapshot, failClosed: Boolean): String = when {
        !s.enabled -> "Shield dimatikan. Lalu lintas jaringan tidak disaring."
        failClosed -> "Shield menyala tetapi proxy tidak berjalan. Sumber jarak jauh ditolak sampai Shield aktif."
        else -> "Shield aktif \u00b7 ${s.blocked} diblokir \u00b7 ${s.passed} diteruskan"
    }
}
