// molina-27g2
package com.molina.filemanager

import com.molina.filemanager.EmbeddedMenu.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedMenuSortTest {
    private fun state(global: Boolean, selecting: Boolean = false, label: String = "Nama \u25B2", count: Int = 0) = EmbeddedMenu.State(
        targetCount = count, hasActions = true, favoriteAvailable = true, isFavorite = false,
        trashAvailable = true, global = global, selecting = selecting, sortLabel = label,
    )

    private fun entries(s: EmbeddedMenu.State) = EmbeddedMenu.build(s)

    @Test fun headerMenuOffersSortWithCurrentLabel() {
        val e = entries(state(global = true)).first { it.action == Action.SORT }
        assertEquals(R.string.menu_sort, e.labelRes)
        assertEquals(listOf<Any>("Nama \u25B2"), e.labelArgs)
    }

    @Test fun sortFollowsRefresh() {
        val a = entries(state(global = true)).map { it.action }
        assertEquals(a.indexOf(Action.REFRESH) + 1, a.indexOf(Action.SORT))
    }

    @Test fun rowSelectingAndEmptyLabelHaveNoSort() {
        assertFalse(Action.SORT in entries(state(global = false, count = 1)).map { it.action })
        assertFalse(Action.SORT in entries(state(global = true, selecting = true, count = 2)).map { it.action })
        assertFalse(Action.SORT in entries(state(global = true, label = "")).map { it.action })
        assertTrue(Action.SORT in entries(state(global = true)).map { it.action })
    }
}
