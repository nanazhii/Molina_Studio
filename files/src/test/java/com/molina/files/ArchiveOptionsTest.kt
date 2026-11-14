// molina-patch26k2
package com.molina.files

import com.molina.core.storage.ArchiveFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveOptionsTest {
    @Test fun formatYangDitawarkanBisaDibuat() {
        assertTrue(ArchiveOptions.FORMATS.all { it.canCompress })
        assertFalse(ArchiveFormat.GZ in ArchiveOptions.FORMATS)
        assertEquals(ArchiveFormat.ZIP, ArchiveOptions.FORMATS.first())
    }

    @Test fun levelPerFormat() {
        assertEquals(listOf(0 to "Simpan", 1 to "Cepat", 6 to "Normal", 9 to "Maksimal"), ArchiveOptions.levels(ArchiveFormat.ZIP))
        assertEquals(listOf(0 to "Simpan", 1 to "Cepat", 6 to "Normal"), ArchiveOptions.levels(ArchiveFormat.SEVEN_Z))
        assertEquals(listOf(1 to "Cepat", 6 to "Normal", 9 to "Maksimal"), ArchiveOptions.levels(ArchiveFormat.TAR_GZ))
        assertEquals(listOf(1 to "Cepat", 6 to "Normal"), ArchiveOptions.levels(ArchiveFormat.TAR_XZ))
        assertEquals(listOf(1 to "Cepat", 9 to "Normal"), ArchiveOptions.levels(ArchiveFormat.TAR_BZ2))
        assertTrue(ArchiveOptions.levels(ArchiveFormat.TAR).isEmpty())
    }

    @Test fun semuaLevelTawaranValidDanTermasukBawaan() {
        for (fmt in ArchiveOptions.FORMATS) {
            val levels = ArchiveOptions.levels(fmt)
            for ((lv, _) in levels) assertEquals(fmt.name, lv, fmt.clampLevel(lv))
            if (levels.isNotEmpty()) assertTrue(fmt.name, levels.any { it.first == fmt.defaultLevel })
        }
    }

    @Test fun permintaanMemberiNamaOperasiDanFormatUntukLog() {
        val a = ArchiveRequest(ArchiveOp.EXTRACT, listOf("Film/Sub.tar.gz"), "Film")
        assertEquals("extract", a.opName)
        assertEquals("tar.gz", a.formatLabel)
        assertEquals("extract-subs", ArchiveRequest(ArchiveOp.EXTRACT_SUBS, listOf("a.zip"), "").opName)
        val c = ArchiveRequest(ArchiveOp.COMPRESS, listOf("x"), "", format = ArchiveFormat.SEVEN_Z)
        assertEquals("compress", c.opName)
        assertEquals("7z", c.formatLabel)
        assertEquals("unknown", ArchiveRequest(ArchiveOp.EXTRACT, listOf("x.bin"), "").formatLabel)
    }
}
