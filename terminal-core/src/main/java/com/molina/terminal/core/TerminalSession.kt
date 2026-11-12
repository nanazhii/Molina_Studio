package com.molina.terminal.core

import kotlinx.coroutines.flow.Flow

data class SessionConfig(
    val command: List<String>,
    val workingDir: String? = null,
    val env: Map<String, String> = emptyMap(),
)

sealed interface SessionEvent {
    data class Output(val text: String) : SessionEvent
    data class Exited(val code: Int) : SessionEvent
    data class Failed(val error: Throwable) : SessionEvent
}

/** Kontrak sesi terminal. UI hanya bergantung pada antarmuka ini. */
interface TerminalSession {
    /** Flow dingin: satu kolektor per sesi. Selesai setelah proses berakhir. */
    val events: Flow<SessionEvent>
    fun write(input: String)
    fun close()
}
