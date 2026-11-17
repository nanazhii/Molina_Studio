// molina-patch13e
package com.molina.cinema

import android.content.Context
import android.view.Surface
import com.molina.log.ExitHooks
import com.molina.core.security.DirectStreamPreflight
import com.molina.core.security.GuardVerdict
import com.molina.core.security.LavfOptions
import com.molina.core.security.MolinaShield
import com.molina.core.security.PlaybackGuard
import com.molina.core.security.SHIELD_BLOCKED_CODE
import com.molina.core.security.ShieldHost
import com.molina.core.security.ShieldRegistry
import com.molina.log.LogLevel
import com.molina.log.MolinaEvents
import com.molina.log.MolinaLog
import com.molina.log.MolinaLogger
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Id sesi pemutar berurutan (p1, p2, ...) = tag Molina/mpv/<id>; awalan p memberi chip amber di Log Viewer. */
object PlayerSessionIds {
    private val counter = AtomicInteger()
    fun next(): String = "p" + counter.incrementAndGet()
}

/** Permukaan video. attach/detach mengikuti siklus SurfaceHolder; aman dipanggil berulang. */
interface PlayerSurface {
    fun attach(surface: Surface)
    fun resize(width: Int, height: Int)
    fun detach()
}

/** Sesi + permukaan hasil MpvPlayerFactory.open. session sudah dibungkus LoggingMolinaPlayerSession. */
class MpvPlayback(val sessionId: String, val session: MolinaPlayerSession, val surface: PlayerSurface)

object MpvPlayerFactory {
    /** null bila libmpv/libplayer tidak termuat atau inisialisasi gagal (alasan tercatat sebagai BUG mpv.*). */
    fun open(
        context: Context,
        logger: MolinaLogger = MolinaLog.logger,
        shield: MolinaShield? = ShieldRegistry.current(),
    ): MpvPlayback? {
        val id = PlayerSessionIds.next()
        if (!MpvNative.isLoaded) {
            MolinaEvents.bug("mpv", "native-missing", "libmpv/libplayer tidak termuat", id = id, logger = logger)
            return null
        }
        val raw = MpvPlayerSession(context.applicationContext, id, logger, shield)
        if (!raw.start()) return null
        return MpvPlayback(id, LoggingMolinaPlayerSession(raw, id, logger), raw)
    }
}

/**
 * Implementasi MolinaPlayerSession di atas MPVLib. Satu sesi aktif pada satu waktu (MpvNativeLog
 * memakai satu jembatan global). Wajib release() agar handle native dan observer dilepas.
 */
class MpvPlayerSession internal constructor(
    private val appContext: Context,
    private val sessionId: String,
    private val logger: MolinaLogger,
    private val shield: MolinaShield? = null,
) : MolinaPlayerSession, PlayerSurface {

    // molina-shield-wiring: guard dan preflight rtmp/rtsp (di thread latar agar DNS tidak memblokir main thread)
    @Volatile private var guard: PlaybackGuard? = null
    private val preflight = DirectStreamPreflight()
    private var io: ExecutorService? = null

    private val mapper = MpvEventMapper()
    private val channel = Channel<PlayerEvent>(64, BufferOverflow.DROP_OLDEST)
    private val released = AtomicBoolean(false)
    @Volatile private var surfaceAttached = false

    override val events: Flow<PlayerEvent> = channel.receiveAsFlow()

    private val observer = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) = Unit
        override fun eventProperty(property: String, value: Long) = Unit
        override fun eventProperty(property: String, value: Boolean) {
            when (property) {
                "pause" -> emit(mapper.onPause(value))
                "eof-reached" -> emit(mapper.onEof(value))
            }
        }
        override fun eventProperty(property: String, value: String) = Unit
        override fun eventProperty(property: String, value: Double) {
            when (property) {
                "time-pos" -> emit(mapper.onTimePos(value))
                "duration" -> mapper.onDuration(value)
            }
        }
        override fun event(eventId: Int) = emit(mapper.onEvent(eventId))
    }

    private fun emit(list: List<PlayerEvent>) { for (e in list) channel.trySend(e) }

    private fun behavior(code: String, fields: Map<String, String> = emptyMap()) =
        MolinaEvents.behavior("mpv", code, fields = fields, id = sessionId, logger = logger)

    internal fun start(): Boolean {
        return try {
            MPVLib.create(appContext)
            MpvNativeLog.attach(sessionId, logger)
            // molina-patch-a: config-dir (cacert.pem + fonts.conf), TLS ketat (gagal tertutup), cache shader
            val cfg = MpvConfig.prepare(appContext)
            MPVLib.setOptionString("config", "yes")
            MPVLib.setOptionString("config-dir", cfg.dir.path)
            for (opt in arrayOf("gpu-shader-cache-dir", "icc-cache-dir"))
                MPVLib.setOptionString(opt, appContext.cacheDir.path)
            MPVLib.setOptionString("tls-verify", "yes")
            MPVLib.setOptionString("tls-ca-file", cfg.caFile.path)
            if (!cfg.certOk) {
                MolinaEvents.bug("mpv", "tls-ca-missing", "cacert.pem tidak tersedia; HTTPS akan gagal verifikasi", level = LogLevel.WARN, id = sessionId, logger = logger)
            }
            if (!cfg.fontsOk) {
                MolinaEvents.bug("mpv", "fonts-conf-missing", "fonts.conf tidak tertulis; font subtitle sistem mungkin tidak ditemukan", level = LogLevel.WARN, id = sessionId, logger = logger)
            }
            MPVLib.setOptionString("vo", "gpu")
            MPVLib.setOptionString("gpu-context", "android")
            MPVLib.setOptionString("opengl-es", "yes")
            MPVLib.setOptionString("hwdec", "mediacodec,mediacodec-copy")
            MPVLib.setOptionString("ao", "audiotrack,opensles")
            // Batas memori: cache demuxer maju 32 MiB, mundur 8 MiB (bawaan mpv jauh lebih besar).
            MPVLib.setOptionString("demuxer-max-bytes", "32MiB")
            MPVLib.setOptionString("demuxer-max-back-bytes", "8MiB")
            // Opsi lavf (blacklist protokol + http_proxy Shield) harus dipasang sebelum init.
            val lavf = LavfOptions.forShield(shield)
            MPVLib.setOptionString("stream-lavf-o", lavf)
            MPVLib.setOptionString("demuxer-lavf-o", lavf)
            guard = PlaybackGuard(shield, shield?.endpoint()?.url())
            MPVLib.init()
            MPVLib.addObserver(observer)
            MPVLib.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            MPVLib.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            MPVLib.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            behavior("session-start")
            ExitHooks.add("mpv-$sessionId") { release() }
            true
        } catch (t: Throwable) {
            if (t is VirtualMachineError) throw t
            MolinaEvents.bug("mpv", "init-failed", "inisialisasi libmpv gagal", t = t, id = sessionId, logger = logger)
            cleanup()
            released.set(true)
            channel.close()
            false
        }
    }

    override fun load(uri: String) {
        if (released.get()) return
        mapper.onLoadRequested(uri)
        val g = guard
        if (g != null) {
            val v = g.checkSync(uri)
            if (v is GuardVerdict.Block) return deny(v, uri)
            if (g.needsPreflight(uri)) {
                val cfg = g.config()
                executor().execute {
                    if (released.get()) return@execute
                    val r = preflight.check(uri, cfg)
                    if (r is GuardVerdict.Block) deny(r, uri) else if (!released.get()) MPVLib.command(arrayOf("loadfile", uri))
                }
                return
            }
        }
        MPVLib.command(arrayOf("loadfile", uri))
    }

    private fun executor(): ExecutorService = synchronized(this) {
        io ?: Executors.newSingleThreadExecutor { r -> Thread(r, "molina-mpv-preflight").apply { isDaemon = true } }.also { io = it }
    }

    /** Catat alasan (kode saja, tidak pernah URL) dan laporkan sebagai galat pemutar. */
    private fun deny(v: GuardVerdict.Block, uri: String) {
        MolinaEvents.bug("mpv", "shield-blocked", "pemuatan diblokir Shield", fields = mapOf("code" to v.code), id = sessionId, level = LogLevel.WARN, logger = logger)
        emit(listOf(PlayerEvent.Error(SHIELD_BLOCKED_CODE, ShieldHost.label(uri)?.let { "$it: ${v.message}" } ?: v.message), PlayerEvent.StateChanged(PlayerState.ERROR)))
    }

    override fun play() { if (!released.get()) MPVLib.setPropertyBoolean("pause", false) }

    override fun pause() { if (!released.get()) MPVLib.setPropertyBoolean("pause", true) }

    override fun seekTo(positionMs: Long) {
        if (released.get()) return
        val sec = String.format(Locale.ROOT, "%.3f", positionMs.coerceAtLeast(0L) / 1000.0)
        MPVLib.command(arrayOf("seek", sec, "absolute"))
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        ExitHooks.remove("mpv-$sessionId")
        synchronized(this) { io?.shutdownNow(); io = null }
        cleanup()
        channel.close()
    }

    private fun cleanup() {
        runCatching { MPVLib.removeObserver(observer) }
        MpvNativeLog.detach()
        detachInternal()
        try {
            MPVLib.destroy()
        } catch (t: Throwable) {
            if (t is VirtualMachineError) throw t
            MolinaEvents.bug("mpv", "release-failed", "MPVLib.destroy gagal", t = t, id = sessionId, logger = logger)
        }
    }

    override fun attach(surface: Surface) {
        if (released.get()) return
        MPVLib.attachSurface(surface)
        MPVLib.setOptionString("force-window", "yes")
        MPVLib.setPropertyString("vo", "gpu")
        surfaceAttached = true
        behavior("surface-attach")
    }

    override fun resize(width: Int, height: Int) {
        if (released.get() || !surfaceAttached) return
        MPVLib.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun detach() { if (!released.get()) detachInternal() }

    private fun detachInternal() {
        if (!surfaceAttached) return
        surfaceAttached = false
        runCatching {
            MPVLib.setPropertyString("vo", "null")
            MPVLib.setOptionString("force-window", "no")
            MPVLib.detachSurface()
        }
        behavior("surface-detach")
    }
}
