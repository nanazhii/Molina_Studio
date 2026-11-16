package com.molina.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerBlocklistTest {
    private val b = TrackerBlocklist()

    @Test fun cocokSufiksDomain() {
        assertTrue(b.isTracker("doubleclick.net"))
        assertTrue(b.isTracker("ads.g.DoubleClick.net."))
        assertFalse(b.isTracker("notdoubleclick.net"))
        assertFalse(b.isTracker("example.com"))
        assertFalse(b.isTracker(""))
    }

    @Test fun pengecualianPenggunaMenang() {
        val allow = setOf(TrackerBlocklist.normalize("Cdn.Mixpanel.com"))
        assertFalse(b.isTracker("cdn.mixpanel.com", allow))
        assertTrue(b.isTracker("api.mixpanel.com", allow))
    }
}
