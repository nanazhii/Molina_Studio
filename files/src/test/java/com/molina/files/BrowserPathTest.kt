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
}
