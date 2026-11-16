package com.molina.studio

import android.app.Application
import com.molina.core.security.LocalShieldProxy
import com.molina.core.security.ShieldRegistry
import com.molina.core.security.TrackerListLoader
import com.molina.log.ExitHooks
import com.molina.log.MolinaEvents
import com.molina.settings.ShieldPrefs
import java.io.File
import kotlin.concurrent.thread

/**
 * Menyalakan Molina Shield saat proses mulai, SEBELUM sesi mpv pertama dibuat (opsi proxy dibaca
 * saat init). Pengaturan dibaca dari ShieldPrefs (diubah di Setelan > Keamanan);
 * bawaan: Shield menyala, pelacak diblokir, LAN ditolak, rtmp/rtsp diperiksa dulu.
 * Daftar pelacak tambahan opsional: /storage/emulated/0/Molina/shield/trackers.txt
 * (+ trackers.txt.sha256 bila ingin memastikan berkas tidak berubah). Gagal membaca tidak fatal.
 */
internal object ShieldBootstrap {
    private const val EXTRA = "/storage/emulated/0/Molina/shield/trackers.txt"

    fun start(app: Application) {
        val cfg = ShieldPrefs.load(app)
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
