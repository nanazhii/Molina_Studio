package com.molina.settings

import com.molina.core.security.DirectStreamPolicy
import com.molina.core.security.ShieldSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShieldPrefsTest {
    @Test fun blockMapsToBlock() {
        val c = ShieldPrefs.toConfig(true, true, false, false, "block")
        assertEquals(DirectStreamPolicy.BLOCK, c.directStreams)
    }

    @Test fun unknownOrNullFallsBackToPreflight() {
        assertEquals(DirectStreamPolicy.PREFLIGHT, ShieldPrefs.toConfig(true, true, false, false, "apa-saja").directStreams)
        assertEquals(DirectStreamPolicy.PREFLIGHT, ShieldPrefs.toConfig(true, true, false, false, null).directStreams)
    }

    @Test fun flagsAreCarried() {
        val c = ShieldPrefs.toConfig(enabled = false, blockTrackers = false, allowLan = true, stripReferer = true, directStreams = "preflight")
        assertEquals(false, c.enabled)
        assertEquals(false, c.blockTrackers)
        assertEquals(true, c.allowLan)
        assertEquals(true, c.stripReferer)
    }

    @Test fun summaryDisabledBeatsFailClosed() {
        assertTrue(ShieldSummary.text(ShieldSnapshot(enabled = false), failClosed = true).startsWith("Shield dimatikan"))
    }

    @Test fun summaryFailClosed() {
        assertTrue(ShieldSummary.text(ShieldSnapshot(enabled = true, running = false), failClosed = true).contains("ditolak"))
    }

    @Test fun summaryActiveShowsCounts() {
        assertEquals(
            "Shield aktif \u00b7 3 diblokir \u00b7 42 diteruskan",
            ShieldSummary.text(ShieldSnapshot(running = true, enabled = true, blocked = 3, passed = 42), failClosed = false),
        )
    }
}
