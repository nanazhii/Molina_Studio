package com.molina.player.home

import android.content.Context
import com.molina.core.common.MolinaPaths
import com.molina.core.common.diag.Diag
import com.molina.core.common.home.Channel
import com.molina.core.common.home.M3uParser
import com.molina.player.PlaybackLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Hasil memuat daftar saluran. [remoteFailed] true bila tautan M3U tersimpan gagal diambil. */
internal class ChannelsLoad(val channels: List<Channel>, val remoteFailed: Boolean)

/**
 * Sumber saluran TV: semua berkas .m3u/.m3u8 di `Molina/channels/` ditambah satu tautan M3U yang disimpan.
 * Satu-satunya kelas yang tahu dari mana daftar datang; layar hanya meminta [load]. Hasil di-cache di memori
 * sampai [load] dengan force=true (menu Segarkan) atau tautan diganti.
 */
internal class ChannelsRepository(
    context: Context,
    private val fetcher: M3uFetcher = M3uFetcher(),
) {
    private val prefs = HomePrefs.of(context)

    @Volatile private var cache: ChannelsLoad? = null

    /** Folder tempat pengguna menaruh berkas daftar. Dibuat saat pertama dimuat agar mudah ditemukan. */
    val folder: File = File(MolinaPaths.DEFAULT_ROOT, FOLDER_NAME)

    var sourceUrl: String?
        get() = prefs.getString(HomePrefs.CHANNELS_URL, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(HomePrefs.CHANNELS_URL) else putString(HomePrefs.CHANNELS_URL, value.trim())
            }.apply()
            cache = null
        }

    fun cached(): ChannelsLoad? = cache

    suspend fun load(force: Boolean = false): ChannelsLoad {
        if (!force) cache?.let { return it }
        return withContext(Dispatchers.IO) { readAll().also { cache = it } }
    }

    private fun readAll(): ChannelsLoad {
        val started = System.nanoTime()
        val found = LinkedHashMap<String, Channel>() // kunci = tautan: duplikat antar-sumber dibuang
        var files = 0
        var remote = false
        var remoteFailed = false

        try {
            folder.mkdirs()
            val candidates = folder.listFiles()?.filter { it.isFile && it.extension.lowercase() in EXTENSIONS }
            for (f in candidates.orEmpty().sortedBy { it.name }) {
                try {
                    val text = f.inputStream().use { it.readTextLimited(M3uFetcher.MAX_BYTES) }
                    for (c in M3uParser.parse(text)) found.putIfAbsent(c.url, c)
                    files++
                } catch (e: Exception) {
                    Diag.bug("player", "channels-failed", "berkas daftar gagal dibaca", e, mapOf("source" to "file"))
                }
            }
        } catch (e: SecurityException) {
            Diag.bug("player", "channels-failed", "folder daftar tidak bisa diakses", e, mapOf("source" to "folder"))
        }

        sourceUrl?.let { url ->
            try {
                for (c in M3uParser.parse(fetcher.fetch(url))) found.putIfAbsent(c.url, c)
                remote = true
            } catch (e: Exception) {
                remoteFailed = true
                Diag.bug("player", "channels-failed", "tautan daftar gagal dimuat", e,
                    mapOf("source" to "url", "target" to PlaybackLog.target(url)))
            }
        }

        val ms = (System.nanoTime() - started) / 1_000_000
        Diag.behavior("player", "channels-loaded", "daftar saluran dimuat", mapOf(
            "count" to found.size.toString(), "files" to files.toString(),
            "remote" to remote.toString(), "ms" to ms.toString(),
        ))
        return ChannelsLoad(found.values.toList(), remoteFailed)
    }

    companion object {
        const val FOLDER_NAME = "channels"
        private val EXTENSIONS = setOf("m3u", "m3u8")
    }
}
