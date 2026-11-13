// molina-patch16
package com.molina.terminal.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionRegistryTest {
    private class Fake(val fail: Boolean = false) : AutoCloseable {
        var closed = 0
        override fun close() {
            closed++
            if (fail) error("gagal")
        }
    }

    @Test
    fun closeAllClosesEachOnceAndEmptiesRegistry() {
        SessionRegistry.closeAll()
        val a = Fake()
        val b = Fake()
        SessionRegistry.add(a)
        SessionRegistry.add(b)
        assertEquals(2, SessionRegistry.closeAll())
        assertEquals(1, a.closed)
        assertEquals(1, b.closed)
        assertEquals(0, SessionRegistry.closeAll())
    }

    @Test
    fun removedSessionIsNotClosed() {
        SessionRegistry.closeAll()
        val a = Fake()
        SessionRegistry.add(a)
        SessionRegistry.remove(a)
        assertEquals(0, SessionRegistry.closeAll())
        assertEquals(0, a.closed)
    }

    @Test
    fun failingCloseDoesNotStopOthers() {
        SessionRegistry.closeAll()
        val bad = Fake(fail = true)
        val ok = Fake()
        SessionRegistry.add(bad)
        SessionRegistry.add(ok)
        assertEquals(2, SessionRegistry.closeAll())
        assertEquals(1, ok.closed)
    }
}
