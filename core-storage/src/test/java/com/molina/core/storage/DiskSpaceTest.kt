// molina-patch26k
package com.molina.core.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiskSpaceTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun kebutuhanKecilCukup() {
        assumeTrue(tmp.root.usableSpace > DiskSpace.MIN_FREE)
        assertFalse(DiskSpace.lacks(tmp.root, 0L))
    }

    @Test fun kebutuhanRaksasaTidakCukupTanpaOverflow() {
        assumeTrue(tmp.root.usableSpace > 0L)
        assertTrue(DiskSpace.lacks(tmp.root, Long.MAX_VALUE))
        assertTrue(DiskSpace.lacks(tmp.root, Long.MAX_VALUE - 1))
    }
}
