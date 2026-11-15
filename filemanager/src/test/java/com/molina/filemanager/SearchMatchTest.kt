// molina-27g3
package com.molina.filemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMatchTest {
    @Test fun allWordsMustMatchIgnoringCase() {
        assertTrue(SearchMatch.matches("Film Aksi 2024.MKV", "aksi mkv"))
        assertFalse(SearchMatch.matches("Film Aksi 2024.MKV", "aksi mp4"))
        assertTrue(SearchMatch.matches("apa saja", "   "))
    }

    @Test fun rangesAreInclusiveAndSorted() {
        assertEquals(listOf(0..1, 4..5), SearchMatch.ranges("abxxabyy", "ab"))
        assertEquals(listOf(0..1, 4..5), SearchMatch.ranges("abxxAByy", "ab"))
    }

    @Test fun overlappingAndAdjacentRangesMerge() {
        assertEquals(listOf(0..3), SearchMatch.ranges("abcd", "abc bcd"))
        assertEquals(listOf(0..3), SearchMatch.ranges("abcd", "ab cd"))
        assertEquals(listOf(0..0, 2..2), SearchMatch.ranges("a.a", "a"))
    }

    @Test fun noMatchOrBlankQueryHasNoRanges() {
        assertEquals(emptyList<IntRange>(), SearchMatch.ranges("abc", "zzz"))
        assertEquals(emptyList<IntRange>(), SearchMatch.ranges("abc", ""))
    }

    @Test fun infoText() {
        assertEquals("Tidak ada hasil", SearchMatch.info(0, false, true))
        assertEquals("3 hasil", SearchMatch.info(3, false, false))
        assertEquals("3 hasil di subfolder", SearchMatch.info(3, false, true))
        assertEquals("500 hasil (dibatasi), persempit kata kunci", SearchMatch.info(500, true, true))
    }
}
