// molina-27i
package com.molina.filemanager

import com.molina.filemanager.EmbeddedMenu.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Embedded27iTest {
    private fun state(
        hasActions: Boolean = true,
        global: Boolean = true,
        selecting: Boolean = false,
        targetCount: Int = 0,
    ) = EmbeddedMenu.State(
        targetCount = targetCount, hasActions = hasActions, favoriteAvailable = true, isFavorite = false,
        trashAvailable = true, global = global, selecting = selecting, transferAvailable = true,
    )

    private fun actions(s: EmbeddedMenu.State) = EmbeddedMenu.build(s).map { it.action }

    @Test
    fun headerMenuEndsWithHamburgerGroupInOrder() {
        val a = actions(state())
        assertEquals(listOf(Action.FAVORITES_LIST, Action.GO_INTERNAL, Action.GO_DEBIAN, Action.OPEN_TRASH), a.takeLast(4))
    }

    @Test
    fun locationsShownEvenWithoutActionsButNoFavoritesOrTrash() {
        val a = actions(state(hasActions = false))
        assertTrue(Action.GO_INTERNAL in a && Action.GO_DEBIAN in a)
        assertFalse(Action.FAVORITES_LIST in a || Action.OPEN_TRASH in a)
    }

    @Test
    fun rowAndSelectingMenusHaveNoHamburgerGroup() {
        val row = actions(state(global = false, targetCount = 1))
        assertFalse(Action.GO_INTERNAL in row || Action.GO_DEBIAN in row)
        val sel = actions(state(selecting = true, targetCount = 2))
        assertFalse(Action.GO_INTERNAL in sel || Action.GO_DEBIAN in sel)
    }
}
