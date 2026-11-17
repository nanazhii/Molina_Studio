package com.molina.cinema

import kotlinx.coroutines.flow.Flow

enum class PlayerState { IDLE, LOADING, PLAYING, PAUSED, ENDED, ERROR }

sealed interface PlayerEvent {
    data class Loaded(val uri: String) : PlayerEvent
    data class StateChanged(val state: PlayerState) : PlayerEvent
    data class Position(val positionMs: Long, val durationMs: Long) : PlayerEvent
    data class Error(val code: Int, val message: String) : PlayerEvent
    data object Ended : PlayerEvent
    // molina-patch-b
    data class TracksChanged(val audio: List<TrackInfo>, val subtitles: List<TrackInfo>) : PlayerEvent
    data class SpeedChanged(val speed: Double) : PlayerEvent
}

/** Kontrak sesi pemutar Molina Cinema. UI hanya bergantung pada antarmuka ini. */
interface MolinaPlayerSession {
    /** Flow dingin: satu kolektor per sesi. */
    val events: Flow<PlayerEvent>
    fun load(uri: String)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    // molina-patch-b: trek, kecepatan, seek relatif, rasio layar, tunda
    fun selectAudio(id: Int)
    /** null = matikan subtitle. */
    fun selectSubtitle(id: Int?)
    /** Dijepit ke PlayerLimits.MIN_SPEED..MAX_SPEED. */
    fun setSpeed(speed: Double)
    /** deltaMs negatif = mundur. */
    fun seekRelative(deltaMs: Long)
    fun setAspect(mode: AspectMode)
    /** Dijepit ke +-PlayerLimits.MAX_DELAY_MS. */
    fun setAudioDelay(ms: Long)
    fun setSubtitleDelay(ms: Long)
    /** Wajib dipanggil untuk melepas handle native (mencegah kebocoran memori). */
    fun release()
}
