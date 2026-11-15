// molina-27l
package com.molina.filemanager

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Embedded27lTest {
    private val xml: String by lazy {
        listOf("src/main/res/values/strings_menu.xml", "filemanager/src/main/res/values/strings_menu.xml")
            .map(::File).first { it.isFile }.readText(Charsets.UTF_8)
    }

    private fun values() = Regex("""<string name="menu_\w+">([^<]*)</string>""").findAll(xml).map { it.groupValues[1] }.toList()

    /** Akar konflik merge 27k: awalan glyph di teks menu. Glyph kini hanya digambar di view. */
    @Test
    fun noMenuStringStartsWithGlyphPrefix() {
        assertTrue(values().isNotEmpty())
        assertTrue(values().none { it.startsWith("\u2630") || it.startsWith("\u22EE") })
    }

    @Test
    fun dynamicLabelsCarryTheirArguments() {
        val s = EmbeddedMenu.State(
            targetCount = 0, hasActions = true, favoriteAvailable = true, isFavorite = false, trashAvailable = true, global = true,
            clipCount = 3, pasteAvailable = true, archiveClipCount = 2, sortLabel = "Nama", searchAvailable = true,
        )
        val e = EmbeddedMenu.build(s)
        assertEquals(listOf<Any>("Nama"), e.first { it.action == EmbeddedMenu.Action.SORT }.labelArgs)
        assertEquals(listOf<Any>(3), e.first { it.action == EmbeddedMenu.Action.PASTE }.labelArgs)
        assertEquals(listOf<Any>(2), e.first { it.action == EmbeddedMenu.Action.EXTRACT_PASTE }.labelArgs)
    }
}
