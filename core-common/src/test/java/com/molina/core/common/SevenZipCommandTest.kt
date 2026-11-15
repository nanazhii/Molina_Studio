// molina-27q
package com.molina.core.common

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SevenZipCommandTest {
    private val out = "/mnt/molina-out/archive.7z"
    private val src = listOf("/mnt/molina-in/0/foto")

    private fun rejects(block: () -> Unit) {
        try { block(); fail("seharusnya ditolak") } catch (e: IllegalArgumentException) { /* ok */ }
    }

    @Test fun createArgsMemakaiSandiDariStdinBukanArgumen() {
        val a = SevenZipCommand.createArgs(out, src, 6, hasPassword = true, encryptNames = false)
        assertTrue("-p" in a)
        assertTrue(a.none { it.startsWith("-p") && it.length > 2 })
        assertTrue("-spd" in a && "-snl" in a && "-bsp1" in a)
        assertEquals(listOf("--", out) + src, a.takeLast(3))
        assertFalse("-mhe=on" in a)
    }

    @Test fun enkripsiNamaButuhSandi() {
        assertTrue("-mhe=on" in SevenZipCommand.createArgs(out, src, 6, hasPassword = true, encryptNames = true))
        rejects { SevenZipCommand.createArgs(out, src, 6, hasPassword = false, encryptNames = true) }
    }

    @Test fun pengecualianTautanLiteral() {
        val a = SevenZipCommand.createArgs(out, src, 5, true, false, listOf("foto/tautan", "foto/a b/c"))
        assertTrue("-x!foto/tautan" in a && "-x!foto/a b/c" in a)
        rejects { SevenZipCommand.createArgs(out, src, 5, true, false, listOf("/mutlak")) }
        rejects { SevenZipCommand.createArgs(out, src, 5, true, false, listOf("a\nb")) }
    }

    @Test fun namaSumberBerwildcardDitolak() {
        rejects { SevenZipCommand.createArgs(out, listOf("/mnt/molina-in/0/a*b"), 6, true, false) }
        rejects { SevenZipCommand.createArgs(out, listOf("/mnt/molina-in/0/a?b"), 6, true, false) }
        SevenZipCommand.createArgs(out, listOf("/mnt/molina-in/0/a*b/ok"), 6, true, false) // bersarang aman
    }

    @Test fun levelDanJalurDivalidasi() {
        rejects { SevenZipCommand.createArgs(out, src, 10, true, false) }
        rejects { SevenZipCommand.createArgs("relatif.7z", src, 5, true, false) }
        rejects { SevenZipCommand.createArgs(out, emptyList(), 5, true, false) }
    }

    @Test fun stdinSatuBarisTanpaBarisBaru() {
        assertArrayEquals("rahasia\n".toByteArray(), SevenZipCommand.stdinFor("rahasia"))
        assertNull(SevenZipCommand.stdinFor(""))
        assertNull(SevenZipCommand.stdinFor(null))
        rejects { SevenZipCommand.stdinFor("a\nb") }
    }

    @Test fun bindHostAman() {
        assertTrue(SevenZipCommand.bindHostOk("/storage/emulated/0/Molina/x"))
        assertFalse(SevenZipCommand.bindHostOk("relatif"))
        assertFalse(SevenZipCommand.bindHostOk("/a:b"))
        assertFalse(SevenZipCommand.bindHostOk("/a/../b"))
    }

    @Test fun prootArgsBerlingkup() {
        val a = SevenZipCommand.prootArgsScoped("/r", listOf("/h/in" to "/mnt/molina-in/0", "/h/out" to "/mnt/molina-out"), "/usr/bin/7z", listOf("a"))
        assertTrue("--bind=/h/in:/mnt/molina-in/0" in a && "--bind=/h/out:/mnt/molina-out" in a)
        assertTrue(a.none { it.startsWith("--bind=/storage") || it.startsWith("--bind=/sdcard") })
        assertEquals(listOf("/usr/bin/7z", "a"), a.takeLast(2))
        rejects { SevenZipCommand.prootArgsScoped("/r", listOf("/h:x" to "/mnt/a"), "/usr/bin/7z", emptyList()) }
    }

    @Test fun klasifikasiGalatKompres() {
        assertEquals(SevenZipCommand.Failure.SOURCE_UNREADABLE, SevenZipCommand.classifyCreate(1, "", true))
        assertEquals(SevenZipCommand.Failure.DISK_FULL, SevenZipCommand.classifyCreate(2, "ERROR: No space left on device", true))
        assertEquals(SevenZipCommand.Failure.OUT_OF_MEMORY, SevenZipCommand.classifyCreate(8, "", true))
        assertEquals(SevenZipCommand.Failure.BAD_COMMAND, SevenZipCommand.classifyCreate(7, "", true))
        assertEquals(SevenZipCommand.Failure.CANCELLED, SevenZipCommand.classifyCreate(255, "", true))
        assertEquals(SevenZipCommand.Failure.UNKNOWN, SevenZipCommand.classifyCreate(2, "aneh", true))
    }

    @Test fun progresPersenTerakhir() {
        assertEquals(42, SevenZipCommand.parseProgress("  3%\u0008\u0008\u0008 42%"))
        assertNull(SevenZipCommand.parseProgress("tanpa angka"))
        assertNull(SevenZipCommand.parseProgress("250%"))
    }
}
