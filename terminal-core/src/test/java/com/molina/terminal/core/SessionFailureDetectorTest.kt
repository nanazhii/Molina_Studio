// molina-patch14
package com.molina.terminal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionFailureDetectorTest {
    private val proot = "/data/user/0/com.molina.studio/files/proot/proot"
    private val assertTail = "./tracee/event.c:714: int handle_tracee_event(Tracee *, int): assertion \"IS_IN_SYSENTER(tracee)\" failed"

    @Test
    fun assertSignatureIsProotAssert() =
        assertEquals(SessionFailure.PROOT_ASSERT, SessionFailureDetector.classify(proot, 1, 900, assertTail))

    @Test
    fun quickNonZeroExitIsEarlyExit() =
        assertEquals(SessionFailure.EARLY_EXIT, SessionFailureDetector.classify(proot, 127, 1_000, ""))

    @Test
    fun lateExitIsNotFailure() = assertNull(SessionFailureDetector.classify(proot, 1, 60_000, ""))

    @Test
    fun nonProotIsIgnored() = assertNull(SessionFailureDetector.classify("/system/bin/sh", 1, 100, assertTail))

    @Test
    fun cleanExitIsNotFailure() = assertNull(SessionFailureDetector.classify(proot, 0, 100, assertTail))
}
