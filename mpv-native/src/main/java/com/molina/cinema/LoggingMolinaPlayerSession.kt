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
            is PlayerEvent.TracksChanged -> behavior("tracks", mapOf("audio" to ev.audio.size.toString(), "sub" to ev.subtitles.size.toString()))
            is PlayerEvent.SpeedChanged -> behavior("speed", mapOf("value" to ev.speed.toString()))
        }
    }

    override fun load(uri: String) { behavior("load", mapOf("file" to MpvLogScrub.fileName(uri))); delegate.load(uri) }
    override fun play() { behavior("play"); delegate.play() }
    override fun pause() { behavior("pause"); delegate.pause() }
    override fun seekTo(positionMs: Long) { logger.d(tag, "seek ke ${positionMs} ms"); delegate.seekTo(positionMs) }
    // molina-patch-b: pilihan trek/rasio = BEHAV; kecepatan, seek relatif, tunda = DEBUG (tanpa nama berkas)
    override fun selectAudio(id: Int) { behavior("audio-select", mapOf("id" to id.toString())); delegate.selectAudio(id) }
    override fun selectSubtitle(id: Int?) { behavior("sub-select", mapOf("id" to (id?.toString() ?: "off"))); delegate.selectSubtitle(id) }
    override fun setSpeed(speed: Double) { logger.d(tag, "kecepatan ${speed}x"); delegate.setSpeed(speed) }
    override fun seekRelative(deltaMs: Long) { logger.d(tag, "seek relatif ${deltaMs} ms"); delegate.seekRelative(deltaMs) }
    override fun setAspect(mode: AspectMode) { behavior("aspect", mapOf("mode" to mode.name)); delegate.setAspect(mode) }
    override fun setAudioDelay(ms: Long) { logger.d(tag, "tunda audio ${ms} ms"); delegate.setAudioDelay(ms) }
    override fun setSubtitleDelay(ms: Long) { logger.d(tag, "tunda subtitle ${ms} ms"); delegate.setSubtitleDelay(ms) }
    override fun release() { behavior("release"); delegate.release() }

    private fun behavior(code: String, fields: Map<String, String> = emptyMap()) =
        MolinaEvents.behavior("mpv", code, fields = fields, id = sessionId, logger = logger)
}
