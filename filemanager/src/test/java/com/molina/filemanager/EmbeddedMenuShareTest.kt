// molina-27g
package com.molina.filemanager

import com.molina.filemanager.EmbeddedMenu.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedMenuShareTest {
    private fun state(
        targetCount: Int = 1,
        selecting: Boolean = false,
        shareAvailable: Boolean = true,
        propsAvailable: Boolean = true,
        global: Boolean = false,
    ) = EmbeddedMenu.State(
        targetCount = targetCount, hasActions = true, favoriteAvailable = true, isFavorite = false,
        trashAvailable = true, global = global, selecting = selecting, transferAvailable = true,
        shareAvailable = shareAvailable, propsAvailable = propsAvailable,
    )

    private fun actions(s: EmbeddedMenu.State) = EmbeddedMenu.build(s).map { it.action }

    @Test
    fun rowMenuOffersShareWhenAvailable() {
        assertTrue(Action.SHARE in actions(state()))
        assertFalse(Action.SHARE in actions(state(shareAvailable = false)))
    }

    @Test
    fun headerMenuHasNoShareAndKeepsSingleProperties() {
        val a = actions(state(targetCount = 0, global = true))
        assertFalse(Action.SHARE in a)
        assertEquals(1, a.count { it == Action.INFO })
    }

    @Test
    fun selectingMenuHasShareAndPropertiesOnlyWhenAvailable() {
        val on = actions(state(targetCount = 3, selecting = true, global = true))
        assertTrue(Action.SHARE in on && Action.INFO in on)
        val off = actions(state(targetCount = 3, selecting = true, global = true, shareAvailable = false, propsAvailable = false))
        assertFalse(Action.SHARE in off || Action.INFO in off)
    }

    @Test
    fun selectingWithoutTargetsDisablesShareAndProperties() {
        val items = EmbeddedMenu.build(state(targetCount = 0, selecting = true, global = true))
        assertFalse(items.first { it.action == Action.SHARE }.enabled)
        assertFalse(items.first { it.action == Action.INFO }.enabled)
    }

    @Test
    fun normalMenuHasExactlyOneProperties() {
        assertEquals(1, actions(state()).count { it == Action.INFO })
    }
}
