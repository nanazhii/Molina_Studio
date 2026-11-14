// molina-patch26k4
package com.molina.files

import com.molina.core.storage.ConflictPolicy
import com.molina.core.storage.ExtractTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveExtractOptionsTest {
    @Test fun kebijakanBerurutanDanBawaanPalingAman() {
        assertEquals(listOf(ConflictPolicy.RENAME, ConflictPolicy.SKIP, ConflictPolicy.OVERWRITE), ArchiveOptions.POLICIES)
        assertEquals(ConflictPolicy.RENAME, ArchiveOptions.POLICIES.first())
    }

    @Test fun tujuanBerurutan() {
        assertEquals(listOf(ExtractTarget.HERE, ExtractTarget.NAMED_FOLDER), ArchiveOptions.TARGETS)
    }

    @Test fun labelUnikDanTidakKosong() {
        val labels = ArchiveOptions.POLICIES.map { ArchiveOptions.policyLabel(it) }
        assertEquals(labels.size, labels.toSet().size)
        assertTrue(labels.all { it.isNotBlank() })
        val targets = ArchiveOptions.TARGETS.map { ArchiveOptions.targetLabel(it) }
        assertEquals(targets.size, targets.toSet().size)
    }

    @Test fun namaKebijakanUntukLog() {
        assertEquals("rename", ArchiveOptions.policyName(ConflictPolicy.RENAME))
        assertEquals("skip", ArchiveOptions.policyName(ConflictPolicy.SKIP))
        assertEquals("overwrite", ArchiveOptions.policyName(ConflictPolicy.OVERWRITE))
    }
}
