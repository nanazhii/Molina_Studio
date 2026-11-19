package com.molina.player.home

import android.content.Context
import com.molina.core.common.home.RecentStore

/** Nama berkas dan kunci SharedPreferences milik paket home (satu tempat, tidak tercecer). */
internal object HomePrefs {
    const val NAME = "molina_home"
    const val RECENT = "recent"
    const val CHANNELS_URL = "channels_url"

    fun of(context: Context) = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}

/** Penyimpanan riwayat di SharedPreferences; logika riwayat sendiri ada di core-common (RecentPlays). */
internal class PrefsRecentStore(context: Context) : RecentStore {
    private val prefs = HomePrefs.of(context)
    override fun read(): String = prefs.getString(HomePrefs.RECENT, "") ?: ""
    override fun write(text: String) {
        prefs.edit().putString(HomePrefs.RECENT, text).apply()
    }
}
