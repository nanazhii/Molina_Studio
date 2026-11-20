package com.molina.player.home

import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import com.molina.core.common.diag.Diag
import com.molina.core.common.home.Channel
import com.molina.core.common.home.RecentEntry
import com.molina.core.common.home.RecentPlays
import com.molina.core.common.home.RecentTitle
import com.molina.player.PlaybackLog
import com.molina.player.R
import com.molina.player.databinding.FragmentMainScreenBinding
import com.molina.player.databinding.ItemHomeRowBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Pusat fitur Home (menu drop, Saluran, Terakhir diputar, Segarkan). Layar cukup membuat satu controller,
 * memanggil [attach], dan melapor lewat [recordPlayed]; fitur baru di Home ditambahkan di paket ini saja.
 * Pemutaran selalu lewat [HomeHost.play], jadi paket ini tidak menyentuh mpv.
 */
internal class HomeController(private val host: HomeHost, private val scope: CoroutineScope) {
    private val recent by lazy { RecentPlays(PrefsRecentStore(host.hostContext())) }
    private val channels by lazy { ChannelsRepository(host.hostContext()) }
    private var binding: FragmentMainScreenBinding? = null

    fun attach(b: FragmentMainScreenBinding) {
        binding = b
        b.cardFiles.setOnClickListener { onAction(HomeAction.FILES) }
        b.cardChannels.setOnClickListener { onAction(HomeAction.CHANNELS) }
        b.cardSettings.setOnClickListener { onAction(HomeAction.SETTINGS) }
        b.cardRefresh.setOnClickListener { onAction(HomeAction.REFRESH) }
        b.recentClearBtn.setOnClickListener { clearRecent() }
        renderRecent()
    }

    fun detach() {
        binding = null
    }

    /** Dilaporkan layar setiap kali pengguna memutar tautan atau berkas dari Home. */
    fun recordPlayed(target: String, title: String = RecentTitle.of(target)) {
        try {
            recent.record(target, title, System.currentTimeMillis())
        } catch (e: Exception) {
            Diag.bug("player", "recent-failed", "riwayat gagal disimpan", e)
            return
        }
        Diag.behavior("player", "recent-recorded", "riwayat dicatat", mapOf("target" to PlaybackLog.target(target)))
        renderRecent()
    }

    private fun onAction(action: HomeAction) {
        Diag.behavior("player", "home-action", "menu dipilih", mapOf("action" to action.name.lowercase()))
        when (action) {
            HomeAction.FILES -> host.openFromDevice()
            HomeAction.CHANNELS -> showChannels()
            HomeAction.SETTINGS -> host.openSettings()
            HomeAction.REFRESH -> refresh()
        }
    }

    private fun showChannels() {
        ChannelsSheet(host.hostContext(), channels, scope) { picked, all -> playChannel(picked, all) }.show()
    }

    /** Memutar saluran terpilih sebagai antrean seluruh daftar (prev/next = ganti saluran). */
    private fun playChannel(channel: Channel, all: List<Channel>) {
        val queue = ChannelPlayback.queue(channel, all)
        Diag.behavior("player", "channels-play", "saluran dibuka", mapOf(
            "target" to PlaybackLog.target(channel.url), "queue" to queue.size.toString(),
        ))
        host.play(ChannelPlayback.target(queue) ?: channel.url)
        recordPlayed(channel.url, channel.name)
    }

    private fun refresh() {
        scope.launch {
            val load = channels.load(force = true)
            if (binding == null) return@launch // layar sudah pergi
            renderRecent()
            Diag.behavior("player", "home-refresh", "Home disegarkan", mapOf("channels" to load.channels.size.toString()))
            val ctx = host.hostContext()
            Toast.makeText(ctx, ctx.getString(R.string.home_refreshed, load.channels.size), Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearRecent() {
        try {
            recent.clear()
        } catch (e: Exception) {
            Diag.bug("player", "recent-failed", "riwayat gagal dihapus", e)
            return
        }
        Diag.behavior("player", "recent-cleared", "riwayat dihapus")
        renderRecent()
    }

    private fun removeRecent(entry: RecentEntry) {
        try {
            recent.remove(entry.target)
        } catch (e: Exception) {
            Diag.bug("player", "recent-failed", "entri riwayat gagal dihapus", e)
            return
        }
        renderRecent()
    }

    private fun renderRecent() {
        val b = binding ?: return
        val entries = try {
            recent.list().take(MAX_SHOWN)
        } catch (e: Exception) {
            Diag.bug("player", "recent-failed", "riwayat gagal dibaca", e)
            emptyList()
        }
        b.recentList.removeAllViews()
        val inflater = LayoutInflater.from(host.hostContext())
        for (entry in entries) {
            val row = ItemHomeRowBinding.inflate(inflater, b.recentList, false)
            row.rowTitle.text = entry.title
            row.rowSub.text = PlaybackLog.target(entry.target) // tanpa jalur penuh, query, atau kredensial
            row.root.setOnClickListener {
                host.play(entry.target)
                recordPlayed(entry.target, entry.title)
            }
            row.root.setOnLongClickListener {
                removeRecent(entry)
                true
            }
            b.recentList.addView(row.root)
        }
        b.recentEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        b.recentClearBtn.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
    }

    private companion object {
        const val MAX_SHOWN = 8
    }
}
