// molina-27e
package com.molina.filemanager

import com.molina.filemanager.EmbeddedMenu.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedMenuTest {
    private fun state(
        targetCount: Int = 1,
        hasActions: Boolean = true,
        favoriteAvailable: Boolean = true,
        isFavorite: Boolean = false,
        trashAvailable: Boolean = true,
        global: Boolean = false,
        selecting: Boolean = false,
        transferAvailable: Boolean = true,
        clipCount: Int = 0,
        pasteAvailable: Boolean = true,
    ) = EmbeddedMenu.State(
        targetCount, hasActions, favoriteAvailable, isFavorite, trashAvailable, global,
        selecting, transferAvailable, clipCount, pasteAvailable,
    )

    private fun actions(s: EmbeddedMenu.State) = EmbeddedMenu.build(s).map { it.action }

    @Test
    fun headerMenuHasGlobalItemsAndNoTargetItems() {
        val a = actions(state(targetCount = 0, trashAvailable = false, global = true))
        assertEquals(Action.REFRESH, a.first())
        assertTrue(Action.FAVORITES_LIST in a && Action.OPEN_TRASH in a && Action.SELECT in a)
        assertFalse(Action.RENAME in a || Action.TRASH in a || Action.DELETE_PERMANENT in a)
        assertFalse(Action.COPY in a || Action.MOVE in a)
    }

    @Test
    fun rowMenuHasNoGlobalItems() {
        val a = actions(state())
        assertFalse(Action.REFRESH in a || Action.FAVORITES_LIST in a || Action.OPEN_TRASH in a)
        assertTrue(a.indexOf(Action.TRASH) in 0 until a.indexOf(Action.DELETE_PERMANENT))
    }

    @Test
    fun outsideManagedRootHasOnlyPermanentDeleteAndDisabledFavoriteAndTransfer() {
        val e = EmbeddedMenu.build(state(favoriteAvailable = false, trashAvailable = false, transferAvailable = false))
        assertFalse(e.any { it.action == Action.TRASH })
        assertTrue(e.any { it.action == Action.DELETE_PERMANENT && it.danger })
        assertFalse(e.single { it.action == Action.FAVORITE }.enabled)
        assertFalse(e.single { it.action == Action.COPY }.enabled)
        assertFalse(e.single { it.action == Action.MOVE }.enabled)
    }

    @Test
    fun favoriteLabelFollowsState() {
        assertEquals("Tambah ke Favorit", EmbeddedMenu.build(state()).single { it.action == Action.FAVORITE }.label)
        assertEquals("Hapus dari Favorit", EmbeddedMenu.build(state(isFavorite = true)).single { it.action == Action.FAVORITE }.label)
    }

    @Test
    fun withoutActionsNoFavoriteNoTrashNoSelect() {
        val a = actions(state(targetCount = 0, hasActions = false, trashAvailable = false, global = true))
        assertFalse(Action.FAVORITE in a || Action.FAVORITES_LIST in a || Action.OPEN_TRASH in a || Action.SELECT in a)
    }

    @Test
    fun selectingMenuHasOnlyBulkActions() {
        val a = actions(state(targetCount = 3, global = true, selecting = true))
        assertEquals(
            listOf(Action.SELECT_ALL, Action.COPY, Action.MOVE, Action.TRASH, Action.DELETE_PERMANENT, Action.EXIT_SELECT),
            a,
        )
    }

    @Test
    fun selectingWithoutTargetsDisablesActionsAndHidesTrash() {
        val e = EmbeddedMenu.build(state(targetCount = 0, trashAvailable = false, global = true, selecting = true))
        assertFalse(e.single { it.action == Action.COPY }.enabled)
        assertFalse(e.single { it.action == Action.MOVE }.enabled)
        assertFalse(e.single { it.action == Action.DELETE_PERMANENT }.enabled)
        assertFalse(e.any { it.action == Action.TRASH })
        assertTrue(e.single { it.action == Action.SELECT_ALL }.enabled)
    }

    @Test
    fun selectLabelDependsOnTarget() {
        assertEquals("Pilih", EmbeddedMenu.build(state()).single { it.action == Action.SELECT }.label)
        assertEquals("Mode pilih", EmbeddedMenu.build(state(targetCount = 0, global = true)).single { it.action == Action.SELECT }.label)
    }

    @Test
    fun pasteShownOnlyWhenClipFilledAndFollowsAvailability() {
        assertFalse(Action.PASTE in actions(state(clipCount = 0)))
        val e = EmbeddedMenu.build(state(clipCount = 2, pasteAvailable = false)).single { it.action == Action.PASTE }
        assertEquals("Tempel di sini (2)", e.label)
        assertFalse(e.enabled)
        assertTrue(EmbeddedMenu.build(state(clipCount = 2)).single { it.action == Action.PASTE }.enabled)
    }
}
