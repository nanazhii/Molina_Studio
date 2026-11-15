// molina-27e
package com.molina.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BerkasDestTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun rootItselfIsEmptyString() {
        val root = tmp.newFolder("Molina")
        assertEquals("", BerkasPaths.destRelative(root, root))
    }

    @Test
    fun subfolderGivesRelative() {
        val root = tmp.newFolder("Molina")
        assertEquals("Film/2026", BerkasPaths.destRelative(root, File(root, "Film/2026")))
    }

    @Test
    fun outsideSiblingPrefixAndTrashAreNull() {
        val root = tmp.newFolder("Molina")
        val other = tmp.newFolder("Molina2")
        assertNull(BerkasPaths.destRelative(root, other))
        assertNull(BerkasPaths.destRelative(root, tmp.root))
        assertNull(BerkasPaths.destRelative(root, File(root, ".molina-trash/abc")))
    }
}
