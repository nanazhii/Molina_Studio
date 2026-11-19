// molina-picker
package com.molina.filemanager

import org.junit.Assert.assertEquals
import org.junit.Test

/** [molina-picker] Folder aktif mode pilih: fokus bila folder, induknya bila berkas, root bila tanpa fokus. */
class PickActiveDirTest {
    private val root = "/r"
    private val dirs = setOf("/r/a", "/r/a/b")

    @Test fun tanpaFokusRoot() = assertEquals(root, FocusNav.activeDir(root, null) { it in dirs })

    @Test fun fokusFolder() = assertEquals("/r/a/b", FocusNav.activeDir(root, "/r/a/b") { it in dirs })

    @Test fun fokusBerkasInduknya() = assertEquals("/r/a", FocusNav.activeDir(root, "/r/a/x.mp4") { it in dirs })

    @Test fun fokusDiLuarRoot() = assertEquals(root, FocusNav.activeDir(root, "/lain/x.mp4") { it in dirs })
}
