// molina-27g3
package com.molina.filemanager

import com.molina.filemanager.EmbeddedMenu.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EmbeddedMenuSearchTest {
    private fun state(global: Boolean, selecting: Boolean = false, active: Boolean = false, available: Boolean = true, count: Int = 0) = EmbeddedMenu.State(
        targetCount = count, hasActions = true, favoriteAvailable = true, isFavorite = false,
        trashAvailable = true, global = global, selecting = selecting, sortLabel = "Nama \u25B2", searchAvailable = available, searchActive = active,
    )

    private fun entries(s: EmbeddedMenu.State) = EmbeddedMenu.build(s)

    @Test fun headerMenuOffersSearchAfterSort() {
        val e = entries(state(global = true))
        val a = e.map { it.action }
        assertEquals(a.indexOf(Action.SORT) + 1, a.indexOf(Action.SEARCH))
        assertEquals(R.string.menu_search, e.first { it.action == Action.SEARCH }.labelRes)
    }

    @Test fun labelFollowsSearchState() {
        val e = entries(state(global = true, active = true)).first { it.action == Action.SEARCH }
        assertEquals(R.string.menu_search_close, e.labelRes)
    }

    @Test fun rowSelectingAndEmptyLabelHaveNoSearch() {
        assertFalse(Action.SEARCH in entries(state(global = false, count = 1)).map { it.action })
        assertFalse(Action.SEARCH in entries(state(global = true, selecting = true, count = 2)).map { it.action })
        assertFalse(Action.SEARCH in entries(state(global = true, available = false)).map { it.action })
    }
}
