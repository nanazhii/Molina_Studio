package com.molina.filemanager

import org.junit.Assert.assertEquals
import org.junit.Test

class NewFileNameTest {
    @Test fun splitMemecahNamaDanEkstensi() {
        assertEquals("skrip.tar" to "gz", NewFileName.split("skrip.tar.gz"))
        assertEquals("skrip" to "sh", NewFileName.split("skrip.sh"))
    }

    @Test fun splitDotfileDanTanpaEkstensi() {
        assertEquals(".bashrc" to "", NewFileName.split(".bashrc"))
        assertEquals("README" to "", NewFileName.split("README"))
        assertEquals("a." to "", NewFileName.split("a."))
    }

    @Test fun joinMenggabungDanMembersihkan() {
        assertEquals("a.sh", NewFileName.join("a", "sh"))
        assertEquals("a.sh", NewFileName.join("a", ".sh"))
        assertEquals("a", NewFileName.join(" a ", ""))
    }
}
