// molina-27g3
package com.molina.filemanager

import com.molina.filemanager.EmbeddedMenu.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EmbeddedMenuSearchTest {
    private fun state(global: Boolean, selecting: Boolean = false, label: String = "Cari\u2026", count: Int = 0) = EmbeddedMenu.State(
        targetCount = count, hasActions = true, favoriteAvailable = true, isFavorite = false,
        trashAvailable = true, global = global, selecting = selecting, sortLabel = "Nama \u25B2", searchLabel = label,
    )

    private fun entries(s: EmbeddedMenu.State) = EmbeddedMenu.build(s)

    @Test fun headerMenuOffersSearchAfterSort() {
        val e = entries(state(global = true))
        val a = e.map { it.action }
        assertEquals(a.indexOf(Action.SORT) + 1, a.indexOf(Action.SEARCH))
        assertEquals("Cari\u2026", e.first { it.action == Action.SEARCH }.label)
    }

    @Test fun labelFollowsSearchState() {
        val e = entries(state(global = true, label = "Tutup pencarian")).first { it.action == Action.SEARCH }
        assertEquals("Tutup pencarian", e.label)
    }

    @Test fun rowSelectingAndEmptyLabelHaveNoSearch() {
        assertFalse(Action.SEARCH in entries(state(global = false, count = 1)).map { it.action })
        assertFalse(Action.SEARCH in entries(state(global = true, selecting = true, count = 2)).map { it.action })
        assertFalse(Action.SEARCH in entries(state(global = true, label = "")).map { it.action })
    }
}
