// molina-27e
package com.molina.filemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedSelectionTest {
    @Test
    fun enterWithFirstSelectsIt() {
        val s = EmbeddedSelection()
        s.enter("/a/b")
        assertTrue(s.active && s.contains("/a/b"))
        assertEquals(1, s.count)
    }

    @Test
    fun enterWithoutFirstIsActiveAndEmpty() {
        val s = EmbeddedSelection()
        s.enter()
        assertTrue(s.active)
        assertEquals(0, s.count)
    }

    @Test
    fun toggleFlipsAndStaysActiveWhenEmpty() {
        val s = EmbeddedSelection()
        s.enter("/a")
        assertFalse(s.toggle("/a"))
        assertTrue(s.active)
        assertEquals(0, s.count)
        assertTrue(s.toggle("/b"))
    }

    @Test
    fun selectAllReplacesAndExitClears() {
        val s = EmbeddedSelection()
        s.enter("/x")
        s.selectAll(listOf("/a", "/b"))
        assertEquals(listOf("/a", "/b"), s.paths)
        s.exit()
        assertFalse(s.active)
        assertEquals(0, s.count)
    }

    @Test
    fun topLevelDropsDescendantsButKeepsSiblingsWithSamePrefix() {
        val r = EmbeddedSelection.topLevel(listOf("/m/a", "/m/a/b", "/m/a/b/c.txt", "/m/ab", "/m/z"))
        assertEquals(listOf("/m/a", "/m/ab", "/m/z"), r)
    }

    @Test
    fun clipPutDeduplicatesAndClearResets() {
        val c = ClipState()
        c.put(TransferMode.MOVE, listOf("/a", "/a", "/b"))
        assertEquals(TransferMode.MOVE, c.mode)
        assertEquals(2, c.count)
        c.clear()
        assertNull(c.mode)
        assertEquals(0, c.count)
    }
}
