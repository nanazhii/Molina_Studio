package com.molina.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPathTest {
    @Test
    fun rootIsEmptyPath() {
        assertTrue(BrowserPath.isRoot(""))
        assertFalse(BrowserPath.isRoot("cinema"))
    }

    @Test
    fun childFromRootAndNested() {
        assertEquals("cinema", BrowserPath.child("", "cinema"))
        assertEquals("cinema/film", BrowserPath.child("cinema", "film"))
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
        assertEquals("Molina/cinema/film", BrowserPath.title("cinema/film"))
    }

    // molina-patch26b
    @Test
    fun entryPathLangsungDanDariHasilPencarian() {
        assertEquals("cinema/a.mp4", BrowserPath.entryPath("cinema", null, "a.mp4"))
        assertEquals("cinema/a.mp4", BrowserPath.entryPath("cinema", "", "a.mp4"))
        assertEquals("a.mp4", BrowserPath.entryPath("", null, "a.mp4"))
        assertEquals("cinema/film/2024/a.mp4", BrowserPath.entryPath("cinema", "film/2024", "a.mp4"))
        assertEquals("film/a.mp4", BrowserPath.entryPath("", "film", "a.mp4"))
    }
}
