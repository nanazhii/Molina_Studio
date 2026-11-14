// molina-patch26a
package com.molina.core.storage

import com.molina.core.common.ErrorKind
import com.molina.core.common.errorOrNull
import com.molina.core.common.getOrNull
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NameRulesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun namaDiTrim() {
        assertEquals("film.mkv", NameRules.validate("  film.mkv ").getOrNull())
    }

    @Test fun namaTidakValid() {
        for (bad in listOf("", "   ", ".", "..", "a/b", "a\u0000b", "x".repeat(256))) {
            assertEquals(bad, ErrorKind.INVALID_NAME, NameRules.validate(bad).errorOrNull()?.kind)
        }
    }

    @Test fun namaPanjang255Diterima() {
        assertEquals("x".repeat(255), NameRules.validate("x".repeat(255)).getOrNull())
    }

    @Test fun uniqueMenambahNomorSebelumEkstensi() {
        File(tmp.root, "a.txt").createNewFile()
        File(tmp.root, "a (1).txt").createNewFile()
        assertEquals("a (2).txt", NameRules.unique(tmp.root, "a.txt").name)
        assertEquals("baru.txt", NameRules.unique(tmp.root, "baru.txt").name)
    }

    @Test fun uniqueUntukNamaTanpaEkstensiDanDotfile() {
        File(tmp.root, "folder").mkdir()
        File(tmp.root, ".env").createNewFile()
        assertEquals("folder (1)", NameRules.unique(tmp.root, "folder").name)
        assertEquals(".env (1)", NameRules.unique(tmp.root, ".env").name)
    }
}
