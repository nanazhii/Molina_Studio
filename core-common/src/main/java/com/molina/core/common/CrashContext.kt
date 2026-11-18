package com.molina.core.common

/**
 * Penanda "sedang di layar mana" untuk laporan crash (baris "Mode"). JVM murni; dibaca dari handler crash
 * di thread mana pun, jadi setiap kolom @Volatile. Hanya mencatat nama layar, tanpa data pengguna.
 */
object CrashContext {
    const val HOST_APP = "Aplikasi"

    /** Activity di depan: [HOST_APP]. */
    @Volatile var host: String? = null

    /** Tab aktif di aplikasi utama. */
    @Volatile var tab: String? = null

    /** Layar penuh di atas tab; null bila tidak ada. */
    @Volatile var overlay: String? = null

    /** Contoh: "Aplikasi > Berkas"; "?" bila belum ada yang tercatat. */
    fun describe(): String {
        val h = host
        return listOfNotNull(h, tab, overlay).joinToString(" > ").ifEmpty { "?" }
    }
}
