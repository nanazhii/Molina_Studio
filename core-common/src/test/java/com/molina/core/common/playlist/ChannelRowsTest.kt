package com.molina.core.common.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelRowsTest {
    private val rows = listOf(
        ChannelRow(1, "Trans7 Digital", 0),
        ChannelRow(2, "Berita Pagi", 1),
        ChannelRow(12, "Film Malam", 2),
    )

    @Test fun formatThreeDigits() {
        assertEquals("007", ChannelRows.number(7))
        assertEquals("003/012", ChannelRows.counter(3, 12))
    }

    @Test fun textFilterIgnoresCase() {
        assertEquals(listOf(2), ChannelRows.filter(rows, "BERITA").map { it.number })
    }

    @Test fun numberFilterKeepsOriginalNumbers() {
        assertEquals(listOf(12), ChannelRows.filter(rows, "12").map { it.number })
    }

    @Test fun blankQueryReturnsAll() {
        assertEquals(rows, ChannelRows.filter(rows, "  "))
    }

    @Test fun enterPicksNumberOrSingleResult() {
        assertEquals(2, ChannelRows.pickOnEnter(rows, "2")?.number)
        assertEquals(12, ChannelRows.pickOnEnter(ChannelRows.filter(rows, "film"), "film")?.number)
        assertNull(ChannelRows.pickOnEnter(rows, "a"))
    }
}
