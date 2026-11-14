// molina-patch26e1
package com.molina.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerFavoritesTest {
    private val kv = MemoryKeyValueStore()
    private val fav = ExplorerFavorites(kv)

    @Test fun kosongAwalnya() {
        assertEquals(emptyList<String>(), fav.paths())
        assertFalse(fav.contains("cinema"))
    }

    @Test fun tambahLaluContains() {
        assertTrue(fav.add("cinema/film"))
        assertTrue(fav.contains("cinema/film"))
        assertTrue(fav.contains("/cinema/film/"))
    }

    @Test fun duplikatDitolak() {
        assertTrue(fav.add("a"))
        assertFalse(fav.add("a/"))
        assertEquals(listOf("a"), fav.paths())
    }

    @Test fun rootDanPathTidakValidDitolak() {
        for (bad in listOf("", "/", "  ", "a/../b", "..", "./a", "a\nb", "a\u0000b")) {
            assertFalse(bad, fav.add(bad))
        }
        assertEquals(emptyList<String>(), fav.paths())
    }

    @Test fun hapus() {
        fav.add("a"); fav.add("b")
        assertTrue(fav.remove("a"))
        assertFalse(fav.remove("a"))
        assertEquals(listOf("b"), fav.paths())
    }

    @Test fun urutanPenambahanTerjaga() {
        fav.add("c"); fav.add("a"); fav.add("b")
        assertEquals(listOf("c", "a", "b"), fav.paths())
    }

    @Test fun loadMenyaringFolderYangHilang() {
        fav.add("ada"); fav.add("hilang")
        assertEquals(listOf("ada"), fav.load { it == "ada" })
        assertEquals(listOf("ada", "hilang"), fav.paths())
    }

    @Test fun tersimpanLintasInstance() {
        fav.add("x/y")
        assertEquals(listOf("x/y"), ExplorerFavorites(kv).paths())
    }

    @Test fun dataRusakTidakMenjatuhkan() {
        kv.putString("favorites_v1", "../luar\n\nbagus\n/\n")
        assertEquals(listOf("bagus", ""), fav.paths())
        assertFalse(fav.contains("../luar"))
    }
}
