package com.molina.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class MolinaResultTest {
    @Test fun successDipetakan() {
        val r = MolinaResult.Success(2).map { it * 3 }
        assertEquals(6, r.getOrNull())
    }

    @Test fun failureTidakDipetakan() {
        val f: MolinaResult<Int> = MolinaResult.Failure(MolinaError(ErrorKind.IO, "x"))
        assertNull(f.map { it + 1 }.getOrNull())
        assertEquals(ErrorKind.IO, f.errorOrNull()?.kind)
    }

    @Test fun catchingMemetakanJenisError() {
        assertEquals(ErrorKind.PERMISSION_DENIED, molinaCatching { throw SecurityException() }.errorOrNull()?.kind)
        assertEquals(ErrorKind.NOT_FOUND, molinaCatching { throw FileNotFoundException() }.errorOrNull()?.kind)
        assertEquals(ErrorKind.IO, molinaCatching { throw IOException() }.errorOrNull()?.kind)
        assertEquals(ErrorKind.UNKNOWN, molinaCatching { error("x") }.errorOrNull()?.kind)
    }

    @Test fun pembatalanTidakDitelan() {
        var dilempar = false
        try {
            molinaCatching { throw kotlin.coroutines.cancellation.CancellationException("batal") }
        } catch (_: kotlin.coroutines.cancellation.CancellationException) {
            dilempar = true
        }
        assertTrue(dilempar)
    }

    @Test fun pesanTidakMemuatPath() {
        val e = molinaCatching { throw IOException("/storage/emulated/0/Molina/rahasia") }.errorOrNull()
        assertTrue(!e!!.message.contains("/storage"))
    }

    @Test fun layoutPathsMengikutRoot() {
        val p = MolinaPaths(File("/tmp/x"))
        assertEquals(File("/tmp/x/logs"), p.logs)
        assertEquals(p.root, p.layout.first())
        assertEquals("/storage/emulated/0/Molina", MolinaPaths().root.path)
    }
}
