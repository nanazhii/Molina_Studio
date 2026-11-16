package com.molina.core.security

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerListLoaderTest {
    @Test fun parseDomainDanFormatHosts() {
        val r = TrackerListLoader.parse(
            """
            # komentar
            ads.example.net
            0.0.0.0 Track.Example.org   # trailing
            127.0.0.1 localhost
            bukan domain
            -bad-.com
            com
            """.trimIndent(),
        )
        assertEquals(setOf("ads.example.net", "track.example.org"), r.domains)
        assertEquals(4, r.rejected)   // "bukan domain", "-bad-.com", "com", dan "localhost" (satu label)
    }

    @Test fun batasEntri() {
        val sb = StringBuilder()
        for (i in 0..TrackerListLoader.MAX_ENTRIES) sb.append("d").append(i).append(".example.net\n")
        val r = TrackerListLoader.parse(sb.toString())
        assertEquals(TrackerListLoader.MAX_ENTRIES, r.domains.size)
        assertNotNull(r.error)
    }

    @Test fun muatBerkasDenganSha() {
        val f = File.createTempFile("trk", ".txt").apply { writeText("ads.example.net\n"); deleteOnExit() }
        val sha = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals(setOf("ads.example.net"), TrackerListLoader.load(f, sha).domains)
        assertEquals("SHA-256 tidak cocok", TrackerListLoader.load(f, "00").error)
        assertEquals("berkas tidak ada", TrackerListLoader.load(File("/tidak/ada.txt")).error)
    }

    @Test fun gabunganDenganBawaan() {
        val b = TrackerListLoader.merged(setOf("ads.example.net"))
        assertTrue(b.isTracker("x.ads.example.net"))
        assertTrue(b.isTracker("sub.doubleclick.net"))
        assertFalse(b.isTracker("example.net"))
        assertNull(TrackerListLoader.load(File.createTempFile("kosong", ".txt").apply { deleteOnExit() }).error)
    }
}
