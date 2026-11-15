// molina-patch26k5d
package com.molina.core.storage

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ArchiveCacheSizeTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun nolBilaFolderCacheBelumAda() {
        assertEquals(0L, ArchiveCache(File(tmp.root, "belum-ada")).size())
    }

    @Test fun menjumlahSlotDanSisaSementara() {
        val root = tmp.newFolder("cache")
        File(root, "s1").mkdirs()
        File(root, "s1/a.bin").writeBytes(ByteArray(10))
        File(root, ".tmp-x").mkdirs()
        File(root, ".tmp-x/b.bin").writeBytes(ByteArray(5))
        assertEquals(15L, ArchiveCache(root).size())
    }

    @Test fun nolSetelahDikosongkan() {
        val root = tmp.newFolder("cache2")
        File(root, "s1").mkdirs()
        File(root, "s1/a.bin").writeBytes(ByteArray(7))
        val cache = ArchiveCache(root)
        assertEquals(7L, cache.clear())
        assertEquals(0L, cache.size())
    }
}
