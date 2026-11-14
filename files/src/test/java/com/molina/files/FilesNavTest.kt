// molina-patch26f
package com.molina.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilesNavTest {
    private fun labels(items: List<FilesNav.Item>) = items.map { it.label }

    @Test
    fun orderIsFavoritesThenStorage() {
        val items = FilesNav.build(listOf("Film/2024"), "")
        assertEquals(
            listOf("Favorit", "Cinema", "Terminal", "Log", "2024", "Penyimpanan", "Molina", "Debian"),
            labels(items),
        )
        assertTrue(items.first().star)
    }

    @Test
    fun rootActiveOnlyAtRoot() {
        assertTrue(FilesNav.build(emptyList(), "").single { it.label == "Molina" }.active)
        assertFalse(FilesNav.build(emptyList(), "cinema").single { it.label == "Molina" }.active)
    }

    @Test
    fun activeMatchesExactPathOnly() {
        val items = FilesNav.build(listOf("Film"), "Film")
        assertTrue(items.single { it.path == "Film" }.active)
        assertFalse(items.single { it.path == "cinema" }.active)
        assertFalse(FilesNav.build(listOf("Film"), "Film/Sub").single { it.path == "Film" }.active)
    }

    @Test
    fun userFavoriteIsRemovableAndBuiltInIsNot() {
        val items = FilesNav.build(listOf("Film"), "")
        assertTrue(items.single { it.path == "Film" }.removable)
        assertFalse(items.single { it.path == "cinema" }.removable)
    }

    @Test
    fun builtInAndRootAreNotDuplicatedFromUserFavorites() {
        val items = FilesNav.build(listOf("cinema", "", "Film"), "")
        assertEquals(1, items.count { it.path == "cinema" })
        assertEquals(1, items.count { it.label == "Molina" })
        assertTrue(FilesNav.isBuiltIn("terminal"))
        assertFalse(FilesNav.isBuiltIn("Film"))
    }

    @Test
    fun debianRowHasNoPath() {
        val d = FilesNav.build(emptyList(), "").single { it.kind == FilesNav.Kind.DEBIAN }
        assertEquals(null, d.path)
    }
}
