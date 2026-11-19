package com.molina.core.common.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {
    @Test fun parsesNameGroupAndUrl() {
        val text = "#EXTM3U\n#EXTINF:-1 tvg-id=\"a\" group-title=\"Berita\",Kanal Satu\nhttps://x.example/a.m3u8\n"  // gate:allow data uji, bukan tautan nyata
        assertEquals(listOf(Channel("Kanal Satu", "https://x.example/a.m3u8", "Berita")), M3uParser.parse(text))  // gate:allow data uji, bukan tautan nyata
    }

    @Test fun commaInsideQuotedAttributeDoesNotSplitTitle() {
        val text = "#EXTINF:-1 tvg-name=\"A, B\" group-title=\"G\",Nama Asli\nhttp://h/s.ts"  // gate:allow data uji, bukan tautan nyata
        assertEquals("Nama Asli", M3uParser.parse(text).single().name)
    }

    @Test fun nonHttpEntriesAreDropped() {
        val text = "#EXTINF:-1,Lokal\nfile:///sdcard/a.mp4\n#EXTINF:-1,Rahasia\n/sdcard/b.mp4\n#EXTINF:-1,Ok\nhttps://h/c.m3u8"  // gate:allow data uji, bukan tautan nyata
        assertEquals(listOf("Ok"), M3uParser.parse(text).map { it.name })
    }

    @Test fun missingTitleFallsBackToHost() {
        assertEquals("host.tv", M3uParser.parse("https://host.tv/live/1.m3u8").single().name)  // gate:allow data uji, bukan tautan nyata
    }

    @Test fun extGrpAppliesOnlyToNextEntry() {
        val text = "#EXTGRP:Olahraga\n#EXTINF:-1,A\nhttps://h/a\n#EXTINF:-1,B\nhttps://h/b"  // gate:allow data uji, bukan tautan nyata
        val r = M3uParser.parse(text)
        assertEquals("Olahraga", r[0].group)
        assertEquals("", r[1].group)
    }

    @Test fun bomCrlfAndLimitAreHandled() {
        val text = "\uFEFF#EXTM3U\r\n#EXTINF:-1,A\r\nhttps://h/a\r\n#EXTINF:-1,B\r\nhttps://h/b\r\n"  // gate:allow data uji, bukan tautan nyata
        assertEquals(2, M3uParser.parse(text).size)
        assertEquals(1, M3uParser.parse(text, maxEntries = 1).size)
    }

    @Test fun filterMatchesAllWordsInNameOrGroup() {
        val all = listOf(Channel("Kanal Satu", "https://h/1", "Berita"), Channel("Kanal Dua", "https://h/2", "Olahraga"))  // gate:allow data uji, bukan tautan nyata
        assertEquals(all, ChannelFilter.apply(all, "  "))
        assertEquals(listOf(all[1]), ChannelFilter.apply(all, "kanal OLAHRAGA"))
        assertTrue(ChannelFilter.apply(all, "tidak ada").isEmpty())
    }
}
