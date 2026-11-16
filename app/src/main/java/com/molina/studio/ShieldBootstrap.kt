package com.molina.studio

import android.app.Application
import android.content.Context
import com.molina.core.security.DirectStreamPolicy
import com.molina.core.security.LocalShieldProxy
import com.molina.core.security.ShieldConfig
import com.molina.core.security.ShieldRegistry
import com.molina.core.security.TrackerListLoader
import com.molina.log.ExitHooks
import com.molina.log.MolinaEvents
import java.io.File
import kotlin.concurrent.thread

/**
 * Menyalakan Molina Shield saat proses mulai, SEBELUM sesi mpv pertama dibuat (opsi proxy dibaca
 * saat init). Pengaturan dibaca dari SharedPreferences "molina_shield" (UI Setelan menyusul);
 * bawaan: Shield menyala, pelacak diblokir, LAN ditolak, rtmp/rtsp diperiksa dulu.
 * Daftar pelacak tambahan opsional: /storage/emulated/0/Molina/shield/trackers.txt
 * (+ trackers.txt.sha256 bila ingin memastikan berkas tidak berubah). Gagal membaca tidak fatal.
 */
internal object ShieldBootstrap {
    private const val PREFS = "molina_shield"
    private const val EXTRA = "/storage/emulated/0/Molina/shield/trackers.txt"

    fun start(app: Application) {
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cfg = ShieldConfig(
            enabled = p.getBoolean("enabled", true),
            blockTrackers = p.getBoolean("block_trackers", true),
            allowLan = p.getBoolean("allow_lan", false),
            stripReferer = p.getBoolean("strip_referer", false),
            directStreams = if (p.getString("direct_streams", "preflight") == "block") DirectStreamPolicy.BLOCK else DirectStreamPolicy.PREFLIGHT,
        )
        val shield = LocalShieldProxy(cfg)
        ShieldRegistry.install(shield)
        shield.start() // gagal start dicatat sebagai BUG net.shield.start-failed; failClosed() otomatis true
        ExitHooks.add("shield") { shield.stop() }
        thread(name = "molina-shield-lists", isDaemon = true) { loadExtra(shield) }
    }

    private fun loadExtra(shield: LocalShieldProxy) {
        val f = File(EXTRA)
        if (!f.isFile) return
        val sha = File("$EXTRA.sha256").takeIf { it.isFile }?.readText()?.trim()?.substringBefore(' ')
        val r = TrackerListLoader.load(f, sha)
        if (r.domains.isEmpty()) {
            MolinaEvents.behavior("net", "shield.trackers-skip", "daftar tambahan tidak dipakai", mapOf("reason" to (r.error ?: "kosong")))
            return
        }
        shield.replaceBlocklist(TrackerListLoader.merged(r.domains))
        MolinaEvents.behavior(
            "net", "shield.trackers-loaded", "daftar pelacak tambahan aktif",
            mapOf("count" to r.domains.size.toString(), "rejected" to r.rejected.toString()),
        )
    }
}
