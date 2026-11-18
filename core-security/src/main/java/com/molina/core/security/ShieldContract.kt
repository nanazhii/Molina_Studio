package com.molina.core.security

import kotlinx.coroutines.flow.StateFlow

/** Tingkat keparahan temuan, menaik: INFO < LOW < MEDIUM < HIGH. */
enum class Severity { INFO, LOW, MEDIUM, HIGH }

/** Kelompok ancaman yang dikenali kontrak keamanan Molina. */
enum class ThreatKind { TRACKER, SPYWARE_RISK, NETWORK_EXPLOIT, POLICY }

/**
 * Satu temuan penyaring atau pemindai. [subject] hanya host atau nama paket, tidak pernah URL
 * lengkap, token, atau isi lalu lintas, supaya aman masuk log dan laporan masalah.
 */
data class Finding(
    val kind: ThreatKind,
    val code: String,
    val severity: Severity,
    val subject: String,
    val detail: String,
)

/** Kontrak pemindai: masukan murni data, keluaran daftar temuan. Tanpa efek samping. */
interface ThreatScanner<in T> {
    fun scan(input: T): List<Finding>
}

/** Pengaturan Shield. Dibuat tak berubah; ganti seluruhnya lewat [MolinaShield.updateConfig]. */
data class ShieldConfig(
    val enabled: Boolean = true,
    val blockTrackers: Boolean = true,
    /** Izinkan alamat jaringan lokal (NAS, DLNA). Default mati: jaringan dianggap tidak tepercaya. */
    val allowLan: Boolean = false,
    val stripReferer: Boolean = false,
    /** Pengecualian pengguna atas daftar pelacak; menang atas daftar blokir. */
    val trackerAllowlist: Set<String> = emptySet(),
)

/** Alamat proxy lokal untuk klien jaringan. [toString] menyembunyikan token agar tidak bocor ke log. */
class ProxyEndpoint(val host: String, val port: Int, val user: String, val token: String) {
    fun url(): String = "http" + "://" + user + ":" + token + "@" + host + ":" + port
    override fun toString(): String = "ProxyEndpoint($host:$port, token=***)"
}

/** Satu permintaan yang diblokir: hanya host dan alasan. */
data class ShieldEvent(val atMs: Long, val host: String, val code: String, val message: String)

data class ShieldSnapshot(
    val running: Boolean = false,
    val enabled: Boolean = true,
    val blocked: Int = 0,
    val passed: Long = 0,
)

/**
 * Kontrak bersama Molina Shield. Terminal dan Setelan hanya bergantung ke antarmuka ini;
 * implementasinya ([LocalShieldProxy]) disuntik dari :app.
 */
interface MolinaShield {
    val snapshot: StateFlow<ShieldSnapshot>
    fun start(): Boolean
    fun stop()
    fun updateConfig(config: ShieldConfig)
    fun config(): ShieldConfig

    /** Alamat proxy untuk klien jaringan, atau null bila Shield dimatikan atau tidak berjalan. */
    fun endpoint(): ProxyEndpoint?

    /** True bila pengguna menyalakan Shield tetapi proxy tidak berjalan: lalu lintas jarak jauh harus ditolak. */
    fun failClosed(): Boolean
    fun recentBlocks(): List<ShieldEvent>
}
