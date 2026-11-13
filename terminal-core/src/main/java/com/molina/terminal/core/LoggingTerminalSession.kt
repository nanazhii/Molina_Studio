// molina-patch13b
package com.molina.terminal.core

import com.molina.log.MolinaLog
import com.molina.log.MolinaLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * Membungkus sesi berbasis Flow dan mencatat siklus hidupnya (tag Molina/term/<id>):
 * BEHAV term.exit / term.close, BUG term.spawn-failed / term.exit-early / term.killed.
 * Isi output/input TIDAK dicatat (bisa memuat kata sandi), hanya ukurannya (level DEBUG).
 */
class LoggingTerminalSession(
    private val delegate: MolinaTerminalSession,
    private val sessionId: String,
    private val logger: MolinaLogger = MolinaLog.logger,
    private val clock: () -> Long = System::currentTimeMillis,
) : MolinaTerminalSession by delegate {

    private val tag = MolinaLogger.tag("term", sessionId)
    private val startedAt = clock()

    override val events: Flow<SessionEvent> = delegate.events.onEach { ev ->
        when (ev) {
            is SessionEvent.Output -> logger.d(tag, "output ${ev.text.length} karakter")
            is SessionEvent.Exited -> TermEvents.exit(logger, sessionId, ev.code, clock() - startedAt)
            is SessionEvent.Failed -> TermEvents.spawnFailed(logger, sessionId, ev.error)
        }
    }

    override fun write(input: String) {
        logger.d(tag, "input ${input.length} karakter")
        delegate.write(input)
    }

    override fun close() {
        TermEvents.close(logger, sessionId, true)
        delegate.close()
    }
}
