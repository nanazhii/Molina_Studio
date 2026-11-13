// molina-patch13
package com.molina.cinema

import com.molina.log.MolinaEvents
import com.molina.log.MolinaLog
import com.molina.log.MolinaLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * Mencatat siklus hidup pemutaran sebagai peristiwa terstruktur (tag Molina/mpv/<id>):
 * BEHAV mpv.load/loaded/state/play/pause/ended/release dan BUG mpv.error.
 * Hanya nama berkas yang dicatat (tanpa path/query); posisi dan seek tidak menjadi peristiwa.
 */
class LoggingMolinaPlayerSession(
    private val delegate: MolinaPlayerSession,
    private val sessionId: String,
    private val logger: MolinaLogger = MolinaLog.logger,
) : MolinaPlayerSession by delegate {

    private val tag = MolinaLogger.tag("mpv", sessionId)

    override val events: Flow<PlayerEvent> = delegate.events.onEach { ev ->
        when (ev) {
            is PlayerEvent.Loaded -> behavior("loaded", mapOf("file" to MpvLogScrub.fileName(ev.uri)))
            is PlayerEvent.StateChanged -> behavior("state", mapOf("state" to ev.state.name))
            is PlayerEvent.Position -> Unit
            is PlayerEvent.Error -> MolinaEvents.bug(
                "mpv", "error", MpvLogScrub.scrub(ev.message),
                fields = mapOf("code" to ev.code.toString()), id = sessionId, logger = logger,
            )
            PlayerEvent.Ended -> behavior("ended")
        }
    }

    override fun load(uri: String) { behavior("load", mapOf("file" to MpvLogScrub.fileName(uri))); delegate.load(uri) }
    override fun play() { behavior("play"); delegate.play() }
    override fun pause() { behavior("pause"); delegate.pause() }
    override fun seekTo(positionMs: Long) { logger.d(tag, "seek ke ${positionMs} ms"); delegate.seekTo(positionMs) }
    override fun release() { behavior("release"); delegate.release() }

    private fun behavior(code: String, fields: Map<String, String> = emptyMap()) =
        MolinaEvents.behavior("mpv", code, fields = fields, id = sessionId, logger = logger)
}
