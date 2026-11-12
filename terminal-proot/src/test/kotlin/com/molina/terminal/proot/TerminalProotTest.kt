package com.molina.terminal.proot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalProotTest {
    @Test
    fun tarPathsCleanAcceptsSafeNames() {
        assertEquals("a/b", TarPaths.clean("a/b"))
        assertEquals("a", TarPaths.clean("./a"))
        assertEquals("", TarPaths.clean("."))
        assertEquals("", TarPaths.clean("./"))
    }

    @Test
    fun tarPathsCleanRejectsUnsafeNames() {
        assertNull(TarPaths.clean("../x"))
        assertNull(TarPaths.clean("a/../b"))
        assertNull(TarPaths.clean("/etc/passwd"))
        assertNull(TarPaths.clean("a\u0000b"))
    }

    @Test
    fun sha256OfAbc() {
        val f = File.createTempFile("molina", ".bin")
        try {
            f.writeBytes("abc".toByteArray())
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Hashing.sha256Hex(f),
            )
        } finally {
            f.delete()
        }
    }

    private fun elf(machine: Int, interp: String?): ByteArray {
        val b = ByteArray(4096)
        b[0] = 0x7f; b[1] = 'E'.code.toByte(); b[2] = 'L'.code.toByte(); b[3] = 'F'.code.toByte()
        b[4] = 2; b[5] = 1
        b[18] = (machine and 0xff).toByte(); b[19] = (machine shr 8).toByte()
        b[32] = 64 // e_phoff
        b[54] = 56 // e_phentsize
        b[56] = 1 // e_phnum
        if (interp != null) {
            b[64] = 3 // PT_INTERP
            val off = 512
            b[64 + 8] = (off and 0xff).toByte(); b[64 + 9] = (off shr 8).toByte()
            val sz = interp.length + 1
            b[64 + 32] = sz.toByte()
            interp.toByteArray().copyInto(b, off)
        } else {
            b[64] = 1 // PT_LOAD
        }
        return b
    }

    @Test
    fun elfStaticArm64() {
        val info = ElfReader.parse(elf(183, null))
        assertNotNull(info)
        assertTrue(info!!.isArm64)
        assertTrue(info.isStatic)
    }

    @Test
    fun elfDynamicArm64() {
        val info = ElfReader.parse(elf(183, "/system/bin/linker64"))!!
        assertTrue(info.isArm64)
        assertFalse(info.isStatic)
        assertEquals("/system/bin/linker64", info.interpreter)
    }

    @Test
    fun elfWrongArchAndNotElf() {
        assertFalse(ElfReader.parse(elf(62, null))!!.isArm64)
        assertNull(ElfReader.parse(ByteArray(100)))
    }
}
