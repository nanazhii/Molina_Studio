// molina-patch13d
package com.molina.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class LogFilterBrandingTest {
    @Test
    fun terminalIdsAreTealKind() {
        assertEquals(SessionKind.TERMINAL, LogFilter.sessionKind("t1"))
        assertEquals(SessionKind.TERMINAL, LogFilter.sessionKind("t12"))
        assertEquals(SessionKind.OTHER, LogFilter.sessionKind("p3"))
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
        assertEquals("Sesi x9", LogFilter.sessionLabel("x9"))
    }
}
