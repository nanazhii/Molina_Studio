package com.molina.cinema

import com.molina.log.MolinaLog
import com.molina.log.MolinaLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * Mencatat siklus hidup pemutaran ke MolinaLogger (tag Molina/mpv/<id>).
 * Hanya nama berkas yang dicatat (tanpa path penuh/query); event posisi tidak dicatat (terlalu sering).
 */
class LoggingMolinaPlayerSession(
    private val delegate: MolinaPlayerSession,
    sessionId: String,
    private val logger: MolinaLogger = MolinaLog.logger,
) : MolinaPlayerSession by delegate {

    private val tag = MolinaLogger.tag("mpv", sessionId)

    override val events: Flow<PlayerEvent> = delegate.events.onEach { ev ->
        when (ev) {
            is PlayerEvent.Loaded -> logger.i(tag, "dimuat: ${fileName(ev.uri)}")
            is PlayerEvent.StateChanged -> logger.i(tag, "status ${ev.state}")
            is PlayerEvent.Position -> Unit
            is PlayerEvent.Error -> logger.e(tag, "error mpv kode ${ev.code}: ${ev.message}")
            PlayerEvent.Ended -> logger.i(tag, "pemutaran selesai")
        }
    }

    override fun load(uri: String) { logger.i(tag, "load: ${fileName(uri)}"); delegate.load(uri) }
    override fun play() { logger.d(tag, "play"); delegate.play() }
    override fun pause() { logger.d(tag, "pause"); delegate.pause() }
    override fun seekTo(positionMs: Long) { logger.d(tag, "seek ke ${positionMs} ms"); delegate.seekTo(positionMs) }
    override fun release() { logger.i(tag, "sesi dilepas"); delegate.release() }

    private fun fileName(uri: String) = uri.substringBefore('?').substringAfterLast('/')
}
