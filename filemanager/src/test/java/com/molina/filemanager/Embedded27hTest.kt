// molina-27h
package com.molina.filemanager

import com.molina.filemanager.EmbeddedMenu.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Embedded27hTest {
    private fun state(
        targetCount: Int,
        favoriteAvailable: Boolean = true,
        archiveAvailable: Boolean = false,
        allArchives: Boolean = false,
    ) = EmbeddedMenu.State(
        targetCount = targetCount, hasActions = true, favoriteAvailable = favoriteAvailable, isFavorite = false,
        trashAvailable = true, global = true, selecting = true, transferAvailable = true,
        archiveAvailable = archiveAvailable, allArchives = allArchives,
    )

    private fun actions(s: EmbeddedMenu.State) = EmbeddedMenu.build(s).map { it.action }

    @Test
    fun selectAndDeselectKeepModeActive() {
        val s = EmbeddedSelection()
        s.select("/a")
        assertTrue(s.active && s.contains("/a"))
        s.select("/a")
        assertEquals(1, s.count)
        s.deselect("/a")
        assertTrue(s.active)
        assertEquals(0, s.count)
        s.deselect("/tidak-ada")
        assertEquals(0, s.count)
    }

    @Test
    fun singleSelectedItemGetsRowMenuActions() {
        val a = actions(state(1))
        assertTrue(Action.RENAME in a)
        assertTrue(Action.FAVORITE in a)
        assertTrue(Action.COPY_PATH in a)
    }

    @Test
    fun singleSelectedFileHasNoFavorite() {
        val a = actions(state(1, favoriteAvailable = false))
        assertTrue(Action.RENAME in a)
        assertFalse(Action.FAVORITE in a)
    }

    @Test
    fun multipleSelectedItemsKeepBulkOnlyMenu() {
        val a = actions(state(3))
        assertFalse(Action.RENAME in a)
        assertFalse(Action.FAVORITE in a)
        assertFalse(Action.COPY_PATH in a)
    }

    @Test
    fun singleSelectedArchiveOffersViewAndTest() {
        val a = actions(state(1, archiveAvailable = true, allArchives = true))
        assertTrue(Action.VIEW_ARCHIVE in a)
        assertTrue(Action.TEST_ARCHIVE in a)
        val many = actions(state(2, archiveAvailable = true, allArchives = true))
        assertFalse(Action.VIEW_ARCHIVE in many)
    }
}
