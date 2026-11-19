package com.molina.core.common.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelFileNameTest {
    @Test fun spacesBecomeDashAndKeepM3u8() = assertEquals("Indo-TV.m3u8", ChannelFileName.of("Indo TV.m3u8"))

    @Test fun pathPartsAreDropped() = assertEquals("passwd.m3u", ChannelFileName.of("../../etc/passwd"))

    @Test fun otherExtensionBecomesM3u() = assertEquals("playlist.m3u", ChannelFileName.of("playlist.txt"))

    @Test fun nullOrEmptyFallsBack() {
        assertEquals("daftar.m3u", ChannelFileName.of(null))
        assertEquals("daftar.m3u", ChannelFileName.of("..m3u"))
    }

    @Test fun baseIsCapped() {
        val name = ChannelFileName.of("a".repeat(200) + ".m3u")
        assertTrue(name.length <= ChannelFileName.MAX_BASE + 4)
    }
}
