package com.molina.player.home

import android.content.Context
import com.molina.core.common.MolinaPaths
import com.molina.core.common.diag.Diag
import com.molina.core.common.home.Channel
import com.molina.core.common.home.ChannelSourceBook
import com.molina.core.common.home.M3uParser
import com.molina.core.security.UrlPolicy
import com.molina.player.PlaybackLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Hasil memuat sumber aktif (menu Segarkan). [remoteFailed] true bila sumber gagal dibaca. */
internal class ChannelsLoad(val channels: List<Channel>, val remoteFailed: Boolean)

/** Hasil memuat satu sumber playlist. */
internal class SourceLoad(val channels: List<Channel>, val failed: Boolean)

/** Satu berkas playlist di folder IPTV Molina. */
internal class LocalPlaylist(val file: File, val sizeBytes: Long)

/**
 * Sumber saluran TV: daftar sumber (tautan M3U atau berkas di `Molina/Media/IPTV/`) yang disimpan di
 * [ChannelSourceBook]. Satu-satunya kelas yang tahu dari mana daftar datang; layar hanya meminta [loadSource]
 * dan [listFiles]. Hasil di-cache di memori per sumber sampai dimuat paksa (Muat atau Segarkan).
 * Berkas hanya dibaca bila berada di dalam [folder]; tautan diperiksa UrlPolicy sebelum diambil.
 */
internal class ChannelsRepository(
    context: Context,
    private val fetcher: M3uFetcher = M3uFetcher(),
) {
    private val prefs = HomePrefs.of(context)
    private val book = ChannelSourceBook(PrefsSourceStore(context))
    private val cache = ConcurrentHashMap<String, List<Channel>>()

    /** Folder tempat pengguna menaruh berkas playlist. */
    val folder: File = File(MolinaPaths.DEFAULT_ROOT, FOLDER_PATH)

    init {
        migrateLegacyUrl()
    }

    fun sources(): List<ChannelSourceBook.Source> = book.list()

    var lastSource: String?
        get() = book.last
        set(value) {
            book.last = value
        }

    /** Menambah sumber dan menjadikannya terakhir dipakai. Melempar IllegalArgumentException bila tidak valid. */
    fun addSource(ref: String) {
        book.add(ref)
    }

    fun removeSource(ref: String) {
        book.remove(ref)
        cache.remove(ref)
    }

    fun cached(ref: String): List<Channel>? = cache[ref]

    suspend fun loadSource(ref: String, force: Boolean = false): SourceLoad {
        if (!force) cache[ref]?.let { return SourceLoad(it, false) }
        val r = withContext(Dispatchers.IO) { read(ref) }
        if (!r.failed) cache[ref] = r.channels
        return r
    }

    /** Memuat sumber aktif (terakhir dipakai, atau yang pertama); kosong bila belum ada sumber. */
    suspend fun load(force: Boolean = false): ChannelsLoad {
        val all = sources()
        val ref = lastSource ?: all.firstOrNull()?.ref ?: return ChannelsLoad(emptyList(), false)
        val r = loadSource(ref, force)
        return ChannelsLoad(r.channels, r.failed)
    }

    /** Berkas .m3u/.m3u8 di [folder], urut nama. Null bila folder tidak bisa dibaca (izin penyimpanan). */
    suspend fun listFiles(): List<LocalPlaylist>? = withContext(Dispatchers.IO) {
        try {
            folder.mkdirs()
            val files = folder.listFiles() ?: return@withContext null
            files.filter { it.isFile && it.extension.lowercase() in EXTENSIONS }
                .sortedBy { it.name.lowercase() }
                .map { LocalPlaylist(it, it.length()) }
        } catch (e: SecurityException) {
            Diag.bug("player", "channels-failed", "folder daftar tidak bisa diakses", e, mapOf("source" to "folder"))
            null
        }
    }

    private fun read(ref: String): SourceLoad {
        val started = System.nanoTime()
        val local = ref.startsWith("/")
        return try {
            val text = if (local) {
                val f = File(ref)
                require(insideFolder(f)) { "berkas di luar folder IPTV" }
                f.inputStream().use { it.readTextLimited(M3uFetcher.MAX_BYTES) }
            } else {
                require(M3uFetcher.isHttp(ref) && UrlPolicy.check(ref) is UrlPolicy.Verdict.Allowed) { "tautan ditolak kebijakan URL" }
                fetcher.fetch(ref)
            }
            val channels = M3uParser.parse(text)
            val ms = (System.nanoTime() - started) / 1_000_000
            Diag.behavior("player", "channels-loaded", "daftar saluran dimuat", mapOf(
                "count" to channels.size.toString(), "source" to if (local) "file" else "url", "ms" to ms.toString(),
            ))
            SourceLoad(channels, false)
        } catch (e: Exception) {
            Diag.bug("player", "channels-failed", "sumber daftar gagal dimuat", e,
                mapOf("source" to if (local) "file" else "url", "target" to PlaybackLog.target(ref)))
            SourceLoad(emptyList(), true)
        }
    }

    private fun insideFolder(f: File): Boolean = try {
        f.canonicalPath.startsWith(folder.canonicalPath + File.separator)
    } catch (e: java.io.IOException) {
        false
    }

    /** Satu tautan tersimpan dari versi lama dipindah menjadi sumber pertama (sekali saja). */
    private fun migrateLegacyUrl() {
        val legacy = prefs.getString(HomePrefs.CHANNELS_URL, null)?.takeIf { it.isNotBlank() } ?: return
        try {
            book.add(legacy)
        } catch (e: IllegalArgumentException) {
            // tautan lama rusak: dibuang saja
        }
        prefs.edit().remove(HomePrefs.CHANNELS_URL).apply()
    }

    companion object {
        const val FOLDER_PATH = "Media/IPTV"
        private val EXTENSIONS = setOf("m3u", "m3u8")
    }
}
