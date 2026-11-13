// molina-patch17
package com.molina.log

import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExitHooksTest {
    @Test
    fun runsEveryHookOnceThenClears() {
        ExitHooks.runAll(100)
        val n = AtomicInteger()
        ExitHooks.add("a") { n.incrementAndGet() }
        ExitHooks.add("b") { n.incrementAndGet() }
        assertTrue(ExitHooks.runAll(2_000).isEmpty())
        assertEquals(2, n.get())
        assertTrue(ExitHooks.runAll(100).isEmpty())
        assertEquals(2, n.get())
    }

    @Test
    fun removedHookDoesNotRun() {
        ExitHooks.runAll(100)
        val n = AtomicInteger()
        ExitHooks.add("x") { n.incrementAndGet() }
        ExitHooks.remove("x")
        ExitHooks.runAll(100)
        assertEquals(0, n.get())
    }

    @Test
    fun throwingHookDoesNotBlockOthers() {
        ExitHooks.runAll(100)
        val n = AtomicInteger()
        ExitHooks.add("bad") { error("gagal") }
        ExitHooks.add("ok") { n.incrementAndGet() }
        assertTrue(ExitHooks.runAll(2_000).isEmpty())
        assertEquals(1, n.get())
    }

    @Test
    fun slowHookIsReportedOnTimeout() {
        ExitHooks.runAll(100)
        ExitHooks.add("slow") { Thread.sleep(3_000) }
        assertEquals(listOf("slow"), ExitHooks.runAll(100))
    }
}
