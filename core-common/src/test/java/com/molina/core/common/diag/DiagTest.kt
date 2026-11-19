package com.molina.core.common.diag

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DiagTest {
    private class Rec : DiagSink {
        val calls = ArrayList<String>()
        var lastError: Throwable? = null
        var lastFields: Map<String, String> = emptyMap()
        override fun behavior(area: String, code: String, summary: String, fields: Map<String, String>) {
            calls.add("B:$area.$code:$summary"); lastFields = fields
        }
        override fun bug(area: String, code: String, summary: String, error: Throwable?, fields: Map<String, String>) {
            calls.add("X:$area.$code:$summary"); lastError = error; lastFields = fields
        }
    }

    @After fun bersihkan() = Diag.install(null)

    @Test fun tanpaSinkTidakMelempar() {
        Diag.behavior("mpv", "command", "cycle")
        Diag.bug("mpv", "command-failed", "cycle", RuntimeException("x"))
    }

    @Test fun sinkMenerimaPeristiwa() {
        val r = Rec()
        Diag.install(r)
        val err = IllegalStateException("gagal")
        Diag.behavior("mpv", "command", "cycle", mapOf("args" to "1"))
        assertEquals(listOf("B:mpv.command:cycle"), r.calls)
        assertEquals("1", r.lastFields["args"])
        Diag.bug("mpv", "command-failed", "loadfile", err, mapOf("ms" to "5"))
        assertEquals("X:mpv.command-failed:loadfile", r.calls.last())
        assertSame(err, r.lastError)
    }

    @Test fun sinkYangMelemparDitelan() {
        Diag.install(object : DiagSink {
            override fun behavior(area: String, code: String, summary: String, fields: Map<String, String>) = error("x")
            override fun bug(area: String, code: String, summary: String, error: Throwable?, fields: Map<String, String>) = error("y")
        })
        Diag.behavior("a", "b")
        Diag.bug("a", "b")
    }

    @Test fun cabutSinkMenghentikanPencatatan() {
        val r = Rec()
        Diag.install(r)
        Diag.install(null)
        Diag.behavior("a", "b")
        assertEquals(0, r.calls.size)
        assertNull(r.lastError)
    }
}
