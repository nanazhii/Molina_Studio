package com.molina.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPathTest {
    @Test
    fun rootIsEmptyPath() {
        assertTrue(BrowserPath.isRoot(""))
        assertFalse(BrowserPath.isRoot("media"))
    }

    @Test
    fun childFromRootAndNested() {
        assertEquals("media", BrowserPath.child("", "media"))
        assertEquals("media/film", BrowserPath.child("media", "film"))
    }

    @Test
    fun parentOfNestedTopLevelAndRoot() {
        assertEquals("a/b", BrowserPath.parent("a/b/c"))
        assertEquals("", BrowserPath.parent("a"))
        assertEquals("", BrowserPath.parent(""))
    }

    @Test
    fun childThenParentReturnsOriginal() {
        assertEquals("logs", BrowserPath.parent(BrowserPath.child("logs", "x")))
        assertEquals("", BrowserPath.parent(BrowserPath.child("", "logs")))
    }

    @Test
    fun titleShowsMolinaRoot() {
        assertEquals("Molina", BrowserPath.title(""))
        assertEquals("Molina/media/film", BrowserPath.title("media/film"))
    }

    // molina-patch26b
    @Test
    fun entryPathLangsungDanDariHasilPencarian() {
        assertEquals("media/a.mp4", BrowserPath.entryPath("media", null, "a.mp4"))
        assertEquals("media/a.mp4", BrowserPath.entryPath("media", "", "a.mp4"))
        assertEquals("a.mp4", BrowserPath.entryPath("", null, "a.mp4"))
        assertEquals("media/film/2024/a.mp4", BrowserPath.entryPath("media", "film/2024", "a.mp4"))
        assertEquals("film/a.mp4", BrowserPath.entryPath("", "film", "a.mp4"))
    }

    @Test
    fun crumbsFromRootAndNested() {
        assertEquals(listOf("Molina" to ""), BrowserPath.crumbs(""))
        assertEquals(
            listOf("Molina" to "", "media" to "media", "film" to "media/film"),
            BrowserPath.crumbs("media/film"),
        )
    }
}
