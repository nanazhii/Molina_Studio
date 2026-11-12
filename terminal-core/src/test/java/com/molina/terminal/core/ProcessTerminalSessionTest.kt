package com.molina.terminal.core

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessTerminalSessionTest {
    @Test
    fun echoProducesOutputAndExitCodeZero() = runBlocking {
        val session = ProcessTerminalSession(SessionConfig(listOf("sh", "-c", "echo molina")))
        val events = session.events.toList()
        val text = events.filterIsInstance<SessionEvent.Output>().joinToString("") { it.text }
        assertTrue(text.contains("molina"))
        assertEquals(0, (events.last() as SessionEvent.Exited).code)
    }

    @Test
    fun missingBinaryEmitsFailed() = runBlocking {
        val session = ProcessTerminalSession(SessionConfig(listOf("/tidak/ada/binary")))
        val events = session.events.toList()
        assertTrue(events.single() is SessionEvent.Failed)
    }
}
