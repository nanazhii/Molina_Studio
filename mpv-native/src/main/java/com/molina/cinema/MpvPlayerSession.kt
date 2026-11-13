// molina-patch13e
package com.molina.cinema

import android.content.Context
import android.view.Surface
import com.molina.log.ExitHooks
import com.molina.log.MolinaEvents
import com.molina.log.MolinaLog
import com.molina.log.MolinaLogger
import java.util.Locale
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
    fun open(context: Context, logger: MolinaLogger = MolinaLog.logger): MpvPlayback? {
        val id = PlayerSessionIds.next()
        if (!MpvNative.isLoaded) {
            MolinaEvents.bug("mpv", "native-missing", "libmpv/libplayer tidak termuat", id = id, logger = logger)
            return null
        }
        val raw = MpvPlayerSession(context.applicationContext, id, logger)
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
) : MolinaPlayerSession, PlayerSurface {

    private val mapper = MpvEventMapper()
    private val channel = Channel<PlayerEvent>(64, BufferOverflow.DROP_OLDEST)
    private val released = AtomicBoolean(false)
    @Volatile private var surfaceAttached = false

    override val events: Flow<PlayerEvent> = channel.receiveAsFlow()

    private val observer = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) = Unit
        override fun eventProperty(property: String, value: Long) {
            when (property) {
                "time-pos" -> emit(mapper.onTimePos(value))
                "duration" -> mapper.onDuration(value)
            }
        }
        override fun eventProperty(property: String, value: Boolean) {
            when (property) {
                "pause" -> emit(mapper.onPause(value))
                "eof-reached" -> emit(mapper.onEof(value))
            }
        }
        override fun eventProperty(property: String, value: String) = Unit
        override fun eventProperty(property: String, value: Double) = Unit
        override fun event(eventId: Int) = emit(mapper.onEvent(eventId))
    }

    private fun emit(list: List<PlayerEvent>) { for (e in list) channel.trySend(e) }

    private fun behavior(code: String, fields: Map<String, String> = emptyMap()) =
        MolinaEvents.behavior("mpv", code, fields = fields, id = sessionId, logger = logger)

    internal fun start(): Boolean {
        return try {
            MPVLib.create(appContext)
            MpvNativeLog.attach(sessionId, logger)
            MPVLib.setOptionString("vo", "gpu")
            MPVLib.setOptionString("gpu-context", "android")
            MPVLib.setOptionString("opengl-es", "yes")
            MPVLib.setOptionString("hwdec", "mediacodec,mediacodec-copy")
            MPVLib.setOptionString("ao", "audiotrack,opensles")
            // Batas memori: cache demuxer maju 32 MiB, mundur 8 MiB (bawaan mpv jauh lebih besar).
            MPVLib.setOptionString("demuxer-max-bytes", "32MiB")
            MPVLib.setOptionString("demuxer-max-back-bytes", "8MiB")
            MPVLib.init()
            MPVLib.addObserver(observer)
            MPVLib.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            MPVLib.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            MPVLib.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            MPVLib.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_INT64)
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
        MPVLib.command(arrayOf("loadfile", uri))
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
