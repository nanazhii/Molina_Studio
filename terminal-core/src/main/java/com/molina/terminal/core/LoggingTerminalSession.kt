package com.molina.terminal.core

import com.molina.log.MolinaLog
import com.molina.log.MolinaLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * Membungkus sesi apa pun dan mencatat siklus hidupnya ke MolinaLogger.
 * Isi output/input TIDAK dicatat (bisa memuat kata sandi), hanya ukurannya.
 */
class LoggingTerminalSession(
    private val delegate: MolinaTerminalSession,
    sessionId: String,
    private val logger: MolinaLogger = MolinaLog.logger,
) : MolinaTerminalSession by delegate {

    private val tag = MolinaLogger.tag("term", sessionId)

    override val events: Flow<SessionEvent> = delegate.events.onEach { ev ->
        when (ev) {
            is SessionEvent.Output -> logger.d(tag, "output ${ev.text.length} karakter")
            is SessionEvent.Exited -> logger.i(tag, "sesi selesai, kode ${ev.code}")
            is SessionEvent.Failed -> logger.e(tag, "sesi gagal dimulai", ev.error)
        }
    }

    override fun write(input: String) {
        logger.d(tag, "input ${input.length} karakter")
        delegate.write(input)
    }

    override fun close() {
        logger.i(tag, "sesi ditutup")
        delegate.close()
    }
}
