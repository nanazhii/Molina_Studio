// molina-27k
package com.molina.filemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Embedded27kTest {
    private fun headerState() = EmbeddedMenu.State(
        targetCount = 0, hasActions = true, favoriteAvailable = true, isFavorite = false,
        trashAvailable = true, global = true,
    )

    @Test
    fun locationGroupLabelsArePlainText() {
        val last = EmbeddedMenu.build(headerState()).takeLast(4)
        assertEquals(listOf("Favorit", "Internal", "Debian", "Sampah"), last.map { it.label })
    }

    @Test
    fun noLabelCarriesHamburgerPrefix() {
        assertTrue(EmbeddedMenu.build(headerState()).none { it.label.contains('\u2630') })
    }
}
