// molina-patch13d
package com.molina.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class LogFilterBrandingTest {
    @Test
    fun terminalIdsAreTealKindAndCinemaIdsAreAmberKind() {
        assertEquals(SessionKind.TERMINAL, LogFilter.sessionKind("t1"))
        assertEquals(SessionKind.TERMINAL, LogFilter.sessionKind("t12"))
        assertEquals(SessionKind.CINEMA, LogFilter.sessionKind("p3"))
    }

    @Test
    fun unknownOrMalformedIdsAreOther() {
        assertEquals(SessionKind.OTHER, LogFilter.sessionKind(null))
        assertEquals(SessionKind.OTHER, LogFilter.sessionKind("x1"))
        assertEquals(SessionKind.OTHER, LogFilter.sessionKind("t"))
        assertEquals(SessionKind.OTHER, LogFilter.sessionKind("tab1"))
    }

    @Test
    fun labelsNameTheArea() {
        assertEquals("Term t1", LogFilter.sessionLabel("t1"))
        assertEquals("Cinema p2", LogFilter.sessionLabel("p2"))
        assertEquals("Sesi x9", LogFilter.sessionLabel("x9"))
    }
}
