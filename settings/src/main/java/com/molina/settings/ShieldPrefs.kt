package com.molina.settings

import android.content.Context
import com.molina.core.security.DirectStreamPolicy
import com.molina.core.security.ShieldConfig
import com.molina.core.security.ShieldRegistry

/**
 * Preferensi Molina Shield (SharedPreferences "molina_shield"). Dibaca ShieldBootstrap saat proses
 * mulai dan diubah layar Keamanan; kunci hanya didefinisikan di sini. Bawaan: Shield menyala,
 * pelacak diblokir, LAN ditolak, rtmp/rtsp diperiksa dulu.
 */
object ShieldPrefs {
    private const val FILE = "molina_shield"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TRACKERS = "block_trackers"
    private const val KEY_LAN = "allow_lan"
    private const val KEY_REFERER = "strip_referer"
    private const val KEY_DIRECT = "direct_streams"
    private const val DIRECT_BLOCK = "block"
    private const val DIRECT_PREFLIGHT = "preflight"

    /** Pemetaan murni (diuji tanpa Android). Nilai tak dikenal jatuh ke PREFLIGHT. */
    fun toConfig(
        enabled: Boolean,
        blockTrackers: Boolean,
        allowLan: Boolean,
        stripReferer: Boolean,
        directStreams: String?,
    ): ShieldConfig = ShieldConfig(
        enabled = enabled,
        blockTrackers = blockTrackers,
        allowLan = allowLan,
        stripReferer = stripReferer,
        directStreams = if (directStreams == DIRECT_BLOCK) DirectStreamPolicy.BLOCK else DirectStreamPolicy.PREFLIGHT,
    )

    fun load(ctx: Context): ShieldConfig {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return toConfig(
            enabled = p.getBoolean(KEY_ENABLED, true),
            blockTrackers = p.getBoolean(KEY_TRACKERS, true),
            allowLan = p.getBoolean(KEY_LAN, false),
            stripReferer = p.getBoolean(KEY_REFERER, false),
            directStreams = p.getString(KEY_DIRECT, DIRECT_PREFLIGHT),
        )
    }

    /** Simpan lalu terapkan ke Shield yang sedang berjalan (daftar pengecualian pelacak dipertahankan). */
    fun update(ctx: Context, change: (ShieldConfig) -> ShieldConfig): ShieldConfig {
        val next = change(load(ctx))
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, next.enabled)
            .putBoolean(KEY_TRACKERS, next.blockTrackers)
            .putBoolean(KEY_LAN, next.allowLan)
            .putBoolean(KEY_REFERER, next.stripReferer)
            .putString(KEY_DIRECT, if (next.directStreams == DirectStreamPolicy.BLOCK) DIRECT_BLOCK else DIRECT_PREFLIGHT)
            .apply()
        val live = ShieldRegistry.current()
        if (live != null) live.updateConfig(next.copy(trackerAllowlist = live.config().trackerAllowlist))
        return next
    }
}
