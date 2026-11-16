package com.molina.core.security

/**
 * Titik akses proses ke Shield yang sedang berjalan, diisi :app saat start (pola sama dengan
 * MolinaLog). Modul engine (mis. mpv-native) membaca [current] tanpa bergantung ke :app.
 */
object ShieldRegistry {
    @Volatile private var shield: MolinaShield? = null

    fun install(s: MolinaShield?) { shield = s }

    fun current(): MolinaShield? = shield
}
