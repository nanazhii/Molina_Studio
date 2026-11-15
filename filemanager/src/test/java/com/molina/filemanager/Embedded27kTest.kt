// molina-27k
package com.molina.filemanager

import org.junit.Assert.assertEquals
import org.junit.Test

class Embedded27kTest {
    private fun headerState() = EmbeddedMenu.State(
        targetCount = 0, hasActions = true, favoriteAvailable = true, isFavorite = false,
        trashAvailable = true, global = true,
    )

    @Test
    fun locationGroupLabelsArePlainText() {
        val last = EmbeddedMenu.build(headerState()).takeLast(4)
        assertEquals(listOf(R.string.menu_favorites, R.string.menu_internal, R.string.menu_debian, R.string.menu_trash_bin), last.map { it.labelRes })
    }
}
