// molina-27g0
package com.molina.filemanager

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChainToTest {
    private val root = File("/storage/emulated/0/Molina")

    @Test
    fun sameFolderGivesEmptyChain() = assertEquals(emptyList<File>(), DrawerFs.chainTo(root, File(root.path)))

    @Test
    fun deepFolderGivesChainFromDirectChild() {
        val dir = File(root, "Film/Seri/S01")
        assertEquals(listOf(File(root, "Film"), File(root, "Film/Seri"), dir), DrawerFs.chainTo(root, dir))
    }

    @Test
    fun outsideRootGivesNull() {
        assertNull(DrawerFs.chainTo(root, File("/storage/emulated/0/Download")))
    }

    @Test
    fun siblingWithSamePrefixIsOutside() {
        assertNull(DrawerFs.chainTo(root, File("/storage/emulated/0/Molina_Studio/app")))
    }
}
