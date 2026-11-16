// molina-27x
package com.molina.files

import com.molina.core.storage.TreeNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveRowTextTest {
    private fun file(name: String, size: Long = 2048) = TreeNode(name, name, false, size, 0, 0, size)
    private fun dir(name: String, files: Int = 0, folders: Int = 0) = TreeNode(name, name, true, 0, files, folders, 0)

    @Test fun berkasMenyebutUkuranDanJenis() {
        assertEquals("2.0 KB \u00B7 FLAC", ArchiveRowText.second(file("a.flac"), false))
    }

    @Test fun berkasTanpaEkstensiHanyaUkuran() {
        assertEquals("2.0 KB", ArchiveRowText.second(file("README"), false))
    }

    @Test fun folderKosong() {
        assertEquals("Folder kosong", ArchiveRowText.second(dir("baru"), false))
    }

    @Test fun folderBerisiMemakaiRingkasan() {
        assertTrue(ArchiveRowText.second(dir("Musik", files = 3, folders = 1), false).startsWith("1 folder"))
    }

    @Test fun hasilCariMemuatLokasi() {
        assertTrue(ArchiveRowText.second(file("a.flac"), true).startsWith("/"))
    }
}
