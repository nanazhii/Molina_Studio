package com.molina.log

import org.junit.Assert.assertEquals
import org.junit.Test

class LogcatOptionsTest {
    @Test
    fun defaultMatchesAdbThreadtime() {
        assertEquals(listOf("logcat", "-v", "threadtime"), LogcatReader.buildCommand(LogcatOptions()))
    }

    @Test
    fun tagFilterWithBuffersAndTail() {
        val o = LogcatOptions(buffers = listOf("main", "crash"), tail = 200, filters = listOf("Molina/term:D", "*:S"))
        assertEquals(
            listOf("logcat", "-v", "threadtime", "-b", "main", "-b", "crash", "-t", "200", "Molina/term:D", "*:S"),
            LogcatReader.buildCommand(o),
        )
    }
}
