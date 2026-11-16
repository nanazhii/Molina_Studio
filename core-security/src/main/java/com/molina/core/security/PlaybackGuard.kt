package com.molina.core.security

import java.net.InetAddress
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

sealed interface GuardVerdict {
    data object Allow : GuardVerdict
    data class Block(val code: String, val message: String) : GuardVerdict
}

/**
 * Pemeriksa sebelum mesin pemutar memuat sebuah URI. [checkSync] tidak menyentuh jaringan (aman di
 * main thread); [needsPreflight] menandai arus langsung (rtmp/rtsp) yang tidak lewat proxy HTTP dan
 * harus diperiksa lewat [DirectStreamPreflight] di thread latar. [shield] null: hanya kebijakan URL dasar.
 * [startedEndpoint] = alamat proxy yang dipakai sesi saat dibuat.
 */
class PlaybackGuard(private val shield: MolinaShield?, private val startedEndpoint: String?) {

    fun config(): ShieldConfig = shield?.config() ?: ShieldConfig(enabled = false)

    fun checkSync(uri: String): GuardVerdict {
        val cfg = config()
        val v = UrlPolicy.check(uri, cfg.allowLan)
        if (v is UrlPolicy.Verdict.Blocked) return GuardVerdict.Block("url." + v.reason.name.lowercase(), v.reason.message)
        if (!cfg.enabled || shield == null) return GuardVerdict.Allow
        when (UrlPolicy.scheme(uri)) {
            "http", "https" -> {
                if (shield.failClosed()) return GuardVerdict.Block("shield.down", "Shield tidak berjalan, sumber jarak jauh ditolak")
                if (shield.endpoint()?.url() != startedEndpoint) {
                    return GuardVerdict.Block("shield.changed", "Shield dimulai ulang, buka ulang pemutar")
                }
            }
            "rtmp", "rtmps", "rtsp" -> if (cfg.directStreams == DirectStreamPolicy.BLOCK) {
                return GuardVerdict.Block("direct.blocked", "arus rtmp/rtsp diblokir oleh pengaturan Shield")
            }
        }
        return GuardVerdict.Allow
    }

    fun needsPreflight(uri: String): Boolean {
        val cfg = config()
        return cfg.enabled && cfg.directStreams == DirectStreamPolicy.PREFLIGHT && UrlPolicy.scheme(uri) in DIRECT
    }

    private companion object { val DIRECT = setOf("rtmp", "rtmps", "rtsp") }
}

/**
 * Pemeriksaan arus langsung (rtmp/rtmps/rtsp) yang tidak lewat proxy: port sensitif, pelacak, dan
 * DNS di-resolve di sini; SEMUA IP hasil resolusi harus lolos (mesin pemutar meresolve sendiri dan
 * bisa memilih IP mana pun). BLOKIR (memanggil jaringan): jalankan di thread latar. Batas yang diketahui:
 * mesin pemutar meresolve ulang, jadi DNS rebinding cepat tidak sepenuhnya tertutup; untuk itu
 * tersedia [DirectStreamPolicy.BLOCK].
 */
class DirectStreamPreflight(
    private val resolver: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    private val blocklist: TrackerBlocklist = TrackerBlocklist(),
    private val timeoutMs: Long = 5_000,
) {
    fun check(uri: String, cfg: ShieldConfig): GuardVerdict {
        val v = UrlPolicy.check(uri, cfg.allowLan)
        if (v is UrlPolicy.Verdict.Blocked) return GuardVerdict.Block("url." + v.reason.name.lowercase(), v.reason.message)
        val scheme = UrlPolicy.scheme(uri) ?: return GuardVerdict.Block("url.bad_scheme", "skema URL tidak diizinkan")
        val authority = uri.trim().substring(scheme.length + 3).takeWhile { it != '/' && it != '?' && it != '#' }
        val hp = parseHostPort(authority, DEFAULT_PORTS[scheme] ?: 0) ?: return GuardVerdict.Block("url.malformed", "URL tidak valid")
        if (!ShieldPolicy.portAllowed(hp.second)) return GuardVerdict.Block("port.sensitive", "port tidak diizinkan")
        val allow = cfg.trackerAllowlist.mapTo(HashSet()) { TrackerBlocklist.normalize(it) }
        if (cfg.blockTrackers && blocklist.isTracker(hp.first, allow)) return GuardVerdict.Block("tracker", "pelacak atau iklan")
        val all = resolve(hp.first)
        if (all.isNullOrEmpty()) return GuardVerdict.Block("dns.failed", "host tidak dapat di-resolve")
        if (all.any { ShieldPolicy.checkAddress(it, cfg.allowLan) is UrlPolicy.Verdict.Blocked }) {
            return GuardVerdict.Block("dns.rebinding", "DNS mengarah ke alamat terlarang")
        }
        return GuardVerdict.Allow
    }

    private fun resolve(host: String): List<InetAddress>? {
        val ex = Executors.newSingleThreadExecutor { r -> Thread(r, "molina-shield-dns").apply { isDaemon = true } }
        val f = ex.submit(Callable { resolver(host) })
        return try {
            f.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) { f.cancel(true); null
        } catch (_: ExecutionException) { null
        } catch (_: InterruptedException) { Thread.currentThread().interrupt(); null
        } finally { ex.shutdownNow() }
    }

    private companion object { val DEFAULT_PORTS = mapOf("rtmp" to 1935, "rtmps" to 443, "rtsp" to 554) }
}
