// molina-27d
package com.molina.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BerkasPathsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun insideRootGivesRelative() {
        val root = tmp.newFolder("Molina")
        assertEquals("Film/a.mkv", BerkasPaths.relative(root, File(root, "Film/a.mkv")))
    }

    @Test
    fun rootItselfIsNull() {
        val root = tmp.newFolder("Molina")
        assertNull(BerkasPaths.relative(root, root))
    }

    @Test
    fun outsideAndSiblingPrefixAreNull() {
        val root = tmp.newFolder("Molina")
        val other = tmp.newFolder("Molina2")
        assertNull(BerkasPaths.relative(root, File(other, "x")))
        assertNull(BerkasPaths.relative(root, tmp.root))
    }

    @Test
    fun trashDirIsNull() {
        val root = tmp.newFolder("Molina")
        assertNull(BerkasPaths.relative(root, File(root, ".molina-trash/abc")))
    }
}
