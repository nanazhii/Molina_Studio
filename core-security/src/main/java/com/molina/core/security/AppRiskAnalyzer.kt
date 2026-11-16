package com.molina.core.security

/** Jejak aplikasi terpasang: data murni, dikumpulkan di lapisan Android (PackageManager). */
data class AppFootprint(
    val packageName: String,
    val permissions: Set<String>,
    val isSystem: Boolean = false,
    /** Nama paket kelas SDK yang terdeteksi di APK (bila pengumpul mampu membacanya). */
    val sdkPackages: Set<String> = emptySet(),
)

/**
 * Penilai risiko aplikasi secara HEURISTIK. Hasilnya petunjuk, bukan vonis malware: kombinasi izin
 * yang lazim dipakai aplikasi pengintai ditandai, dan SDK pelacak dilaporkan. Aplikasi sistem dilewati.
 */
class AppRiskAnalyzer : ThreatScanner<AppFootprint> {

    override fun scan(input: AppFootprint): List<Finding> {
        if (input.isSystem) return emptyList()
        val pkg = input.packageName
        val out = ArrayList<Finding>()
        val sensitive = input.permissions.filter { it in SENSITIVE }
        val control = input.permissions.filter { it in CONTROL }
        val net = (P + "INTERNET") in input.permissions
        val spyControl = control.any { it == P + "BIND_ACCESSIBILITY_SERVICE" || it == P + "BIND_NOTIFICATION_LISTENER_SERVICE" }

        when {
            net && spyControl && sensitive.isNotEmpty() -> out += finding(
                "spy.surveillance-combo", Severity.HIGH, pkg,
                "izin kontrol layar/notifikasi + data sensitif + internet (${sensitive.size} izin sensitif)",
            )
            net && sensitive.size >= 3 -> out += finding(
                "spy.many-sensitive", Severity.MEDIUM, pkg,
                "${sensitive.size} izin sensitif + internet",
            )
            control.isNotEmpty() && sensitive.isNotEmpty() -> out += finding(
                "spy.control-sensitive", Severity.LOW, pkg,
                "izin kontrol (${control.size}) bersama izin sensitif (${sensitive.size})",
            )
        }
        val sdks = TrackerDomains.SDK_PREFIXES.filter { p -> input.sdkPackages.any { it == p || it.startsWith("$p.") } }
        for (s in sdks.sorted()) {
            out += Finding(ThreatKind.TRACKER, "tracker.sdk", Severity.LOW, pkg, "SDK pelacak: $s")
        }
        return out
    }

    private fun finding(code: String, sev: Severity, pkg: String, detail: String) =
        Finding(ThreatKind.SPYWARE_RISK, code, sev, pkg, detail)

    private companion object {
        const val P = "android.permission."
        val SENSITIVE = setOf(
            "RECORD_AUDIO", "CAMERA", "READ_SMS", "RECEIVE_SMS", "READ_CALL_LOG", "READ_CONTACTS",
            "ACCESS_FINE_LOCATION", "ACCESS_BACKGROUND_LOCATION",
        ).map { P + it }.toSet()
        val CONTROL = setOf(
            "BIND_ACCESSIBILITY_SERVICE", "BIND_NOTIFICATION_LISTENER_SERVICE", "BIND_DEVICE_ADMIN",
            "SYSTEM_ALERT_WINDOW", "REQUEST_INSTALL_PACKAGES",
        ).map { P + it }.toSet()
    }
}
