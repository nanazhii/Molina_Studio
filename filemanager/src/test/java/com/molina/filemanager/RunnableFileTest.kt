package com.molina.filemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunnableFileTest {
    @Test fun interpreterMenurutEkstensi() {
        assertEquals("bash", RunnableFile.interpreter("a.sh"))
        assertEquals("python3", RunnableFile.interpreter("a.PY"))
        assertEquals("node", RunnableFile.interpreter("a.js"))
        assertNull(RunnableFile.interpreter("a.txt"))
        assertNull(RunnableFile.interpreter("README"))
    }

    @Test fun ekstensiTakDikenaliMenghasilkanNull() {
        assertNull(RunnableFile.shellLine("a.txt", "'/d'", "'/d/a.txt'", true))
    }

    @Test fun shellAndroidMemakaiShBukanBash() {
        val l = RunnableFile.shellLine("a.sh", "'/d'", "'/d/a.sh'", false)!!
        assertTrue(l.startsWith("cd '/d' && "))
        assertTrue(l.contains("sh '/d/a.sh'"))
        assertTrue(l.contains("sh tidak ditemukan di shell ini"))
        assertTrue(l.endsWith("\r"))
    }

    @Test fun prootMemberiPetunjukApt() {
        val l = RunnableFile.shellLine("a.py", "'/d'", "'/d/a.py'", true)!!
        assertTrue(l.contains("python3 '/d/a.py'"))
        assertTrue(l.contains("apt install python3"))
    }

    @Test fun logMembungkusDenganTee() {
        val log = RunLog("'/d/logs'", "'/d/logs/a'")
        val l = RunnableFile.shellLine("a.py", "'/d'", "'/d/a.py'", true, log)!!
        assertTrue(l.contains("mkdir -p '/d/logs'"))
        assertTrue(l.contains("python3 -u '/d/a.py' 2>&1 | tee '/d/logs/a'-\$(date +%Y%m%d"))
    }
}
