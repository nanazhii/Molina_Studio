// molina-27d2
package com.molina.filemanager

import com.molina.filemanager.EmbeddedMenu.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedMenuTest {
    private fun state(
        hasTarget: Boolean = true,
        hasActions: Boolean = true,
        favoriteAvailable: Boolean = true,
        isFavorite: Boolean = false,
        trashAvailable: Boolean = true,
        global: Boolean = false,
    ) = EmbeddedMenu.State(hasTarget, hasActions, favoriteAvailable, isFavorite, trashAvailable, global)

    private fun actions(s: EmbeddedMenu.State) = EmbeddedMenu.build(s).map { it.action }

    @Test
    fun headerMenuHasGlobalItemsAndNoTargetItems() {
        val a = actions(state(hasTarget = false, trashAvailable = false, global = true))
        assertEquals(Action.REFRESH, a.first())
        assertTrue(Action.FAVORITES_LIST in a && Action.OPEN_TRASH in a)
        assertFalse(Action.RENAME in a || Action.TRASH in a || Action.DELETE_PERMANENT in a)
    }

    @Test
    fun rowMenuHasNoGlobalItems() {
        val a = actions(state())
        assertFalse(Action.REFRESH in a || Action.FAVORITES_LIST in a || Action.OPEN_TRASH in a)
        assertTrue(a.indexOf(Action.TRASH) in 0 until a.indexOf(Action.DELETE_PERMANENT))
    }

    @Test
    fun outsideManagedRootHasOnlyPermanentDeleteAndDisabledFavorite() {
        val e = EmbeddedMenu.build(state(favoriteAvailable = false, trashAvailable = false))
        assertFalse(e.any { it.action == Action.TRASH })
        assertTrue(e.any { it.action == Action.DELETE_PERMANENT && it.danger })
        assertFalse(e.single { it.action == Action.FAVORITE }.enabled)
    }

    @Test
    fun favoriteLabelFollowsState() {
        assertEquals("Tambah ke Favorit", EmbeddedMenu.build(state()).single { it.action == Action.FAVORITE }.label)
        assertEquals("Hapus dari Favorit", EmbeddedMenu.build(state(isFavorite = true)).single { it.action == Action.FAVORITE }.label)
    }

    @Test
    fun withoutActionsNoFavoriteNoTrash() {
        val a = actions(state(hasActions = false, trashAvailable = false, global = true))
        assertFalse(Action.FAVORITE in a || Action.FAVORITES_LIST in a || Action.OPEN_TRASH in a)
    }
}
