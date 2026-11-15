// molina-27o
package com.molina.filemanager

import org.junit.Assert.assertEquals
import org.junit.Test

class Embedded27oTest {
    private val root = "/storage/emulated/0/Molina"
    private val dirs = setOf("$root/Film", "$root/Film/2026")

    @Test
    fun effectiveIgnoresFocusOutsideRoot() {
        assertEquals(root, FocusNav.effective(root, null))
        assertEquals(root, FocusNav.effective(root, "/storage/emulated/0/Lain"))
        assertEquals(root, FocusNav.effective(root, "$root-x/a"))
        assertEquals("$root/Film", FocusNav.effective(root, "$root/Film"))
    }

    @Test
    fun activeDirIsFocusedFolderOrParentOfFile() {
        val isDir = { p: String -> p in dirs }
        assertEquals(root, FocusNav.activeDir(root, null, isDir))
        assertEquals("$root/Film", FocusNav.activeDir(root, "$root/Film", isDir))
        assertEquals("$root/Film/2026", FocusNav.activeDir(root, "$root/Film/2026/a.mkv", isDir))
        assertEquals(root, FocusNav.activeDir(root, "$root/catatan.txt", isDir))
    }

    @Test
    fun upWalksTowardRootThenLeavesIt() {
        assertEquals("$root/Film", FocusNav.up(root, "$root/Film/2026"))
        assertEquals("$root/Film/2026", FocusNav.up(root, "$root/Film/2026/a.mkv"))
        assertEquals(FocusNav.ROOT_FOCUS, FocusNav.up(root, "$root/Film"))
        assertEquals(FocusNav.UP_AT_ROOT, FocusNav.up(root, null))
        assertEquals(FocusNav.UP_AT_ROOT, FocusNav.up(root, root))
    }

    @Test
    fun extLabel() {
        assertEquals("KT", ExtLabel.of("Main.kt"))
        assertEquals("GZ", ExtLabel.of("skrip.tar.gz"))
        assertEquals("PROP", ExtLabel.of("a.properties"))
        assertEquals("", ExtLabel.of("README"))
        assertEquals("", ExtLabel.of(".bashrc"))
        assertEquals("", ExtLabel.of("akhir."))
    }
}
