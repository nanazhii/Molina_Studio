package com.molina.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRiskAnalyzerTest {
    private val p = "android.permission."
    private val a = AppRiskAnalyzer()

    private fun perms(vararg n: String) = n.map { p + it }.toSet()

    @Test fun kombinasiPengintaiDitandaiTinggi() {
        val f = a.scan(AppFootprint("x.y", perms("INTERNET", "BIND_ACCESSIBILITY_SERVICE", "RECORD_AUDIO")))
        assertEquals(Severity.HIGH, f.single().severity)
        assertEquals("spy.surveillance-combo", f.single().code)
    }

    @Test fun banyakIzinSensitifDitandaiSedang() {
        val f = a.scan(AppFootprint("x.y", perms("INTERNET", "CAMERA", "READ_SMS", "READ_CONTACTS")))
        assertEquals("spy.many-sensitive", f.single().code)
    }

    @Test fun aplikasiBiasaBersih() {
        assertTrue(a.scan(AppFootprint("x.y", perms("INTERNET", "CAMERA"))).isEmpty())
    }

    @Test fun aplikasiSistemDilewati() {
        assertTrue(a.scan(AppFootprint("sys", perms("INTERNET", "BIND_ACCESSIBILITY_SERVICE", "RECORD_AUDIO"), isSystem = true)).isEmpty())
    }

    @Test fun sdkPelacakDilaporkan() {
        val sdk = TrackerDomains.SDK_PREFIXES.sorted().first() + ".core"
        val f = a.scan(AppFootprint("x.y", emptySet(), sdkPackages = setOf(sdk, "com.example.app")))
        assertEquals(ThreatKind.TRACKER, f.single().kind)
        assertEquals("x.y", f.single().subject)
    }
}
