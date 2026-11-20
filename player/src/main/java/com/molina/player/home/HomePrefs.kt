package com.molina.player.home

import android.content.Context
import com.molina.core.common.home.ChannelSourceStore
import com.molina.core.common.home.RecentStore

/** Nama berkas dan kunci SharedPreferences milik paket home (satu tempat, tidak tercecer). */
internal object HomePrefs {
    const val NAME = "molina_home"
    const val RECENT = "recent"
    const val CHANNELS_URL = "channels_url" // lama: satu tautan; dimigrasikan ke CHANNEL_SOURCES oleh ChannelsRepository
    const val CHANNEL_SOURCES = "channel_sources"

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

/** Penyimpanan buku sumber playlist; logikanya ada di core-common (ChannelSourceBook). */
internal class PrefsSourceStore(context: Context) : ChannelSourceStore {
    private val prefs = HomePrefs.of(context)
    override fun read(): String = prefs.getString(HomePrefs.CHANNEL_SOURCES, "") ?: ""
    override fun write(text: String) {
        prefs.edit().putString(HomePrefs.CHANNEL_SOURCES, text).apply()
    }
}
