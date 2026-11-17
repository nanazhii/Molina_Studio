package com.molina.cinema

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLauncherTest {
    @Test fun kosongDitolakSebagaiEmpty() {
        assertEquals(LaunchPlan.Empty, PlayerLauncher.plan("   ", false))
    }

    @Test fun pathLokalDanHttpsDiizinkan() {
        assertEquals(LaunchPlan.Open("/storage/emulated/0/a.mp4"), PlayerLauncher.plan(" /storage/emulated/0/a.mp4 ", false))
        assertEquals(LaunchPlan.Open("https://example.com/v.mp4"), PlayerLauncher.plan("https://example.com/v.mp4", false)) // gate:allow URL contoh uji
    }

    @Test fun skemaBerbahayDanLoopbackDiblokir() {
        assertTrue(PlayerLauncher.plan("concat:/data/x|/data/y", false) is LaunchPlan.Blocked)
        assertTrue(PlayerLauncher.plan("http://127.0.0.1/x", true) is LaunchPlan.Blocked) // gate:allow URL contoh uji
    }

    @Test fun alamatLanHanyaBilaDiizinkan() {
        assertTrue(PlayerLauncher.plan("http://192.168.1.5/v.mp4", false) is LaunchPlan.Blocked) // gate:allow URL contoh uji
        assertEquals(LaunchPlan.Open("http://192.168.1.5/v.mp4"), PlayerLauncher.plan("http://192.168.1.5/v.mp4", true)) // gate:allow URL contoh uji
    }
}
