package com.molina.mpv

import kotlinx.coroutines.flow.Flow

enum class PlayerState { IDLE, LOADING, PLAYING, PAUSED, ENDED, ERROR }

sealed interface PlayerEvent {
    data class Loaded(val uri: String) : PlayerEvent
    data class StateChanged(val state: PlayerState) : PlayerEvent
    data class Position(val positionMs: Long, val durationMs: Long) : PlayerEvent
    data class Error(val code: Int, val message: String) : PlayerEvent
    data object Ended : PlayerEvent
}

/** Kontrak sesi pemutar Molina Cinema. UI hanya bergantung pada antarmuka ini. */
interface MolinaPlayerSession {
    /** Flow dingin: satu kolektor per sesi. */
    val events: Flow<PlayerEvent>
    fun load(uri: String)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    /** Wajib dipanggil untuk melepas handle native (mencegah kebocoran memori). */
    fun release()
}
