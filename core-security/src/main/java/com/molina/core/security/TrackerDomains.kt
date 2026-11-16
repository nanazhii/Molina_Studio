package com.molina.core.security

/**
 * Data daftar blokir pelacak. Berkas ini sengaja dikecualikan dari pemeriksaan pola SDK di
 * scripts/telemetry_gate.py (BLOCKLIST_FILES) karena isinya memang nama pelacak yang diblokir.
 * Jangan tambahkan logika di sini, hanya data.
 */
internal object TrackerDomains {
    /** Domain iklan, analitik, dan telemetri. Dicocokkan per sufiks domain. */
    val DEFAULT: Set<String> = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "google-analytics.com", "googletagmanager.com", "googletagservices.com", "app-measurement.com",
        "crashlytics.com", "scorecardresearch.com", "adnxs.com", "taboola.com", "outbrain.com",
        "criteo.com", "criteo.net", "hotjar.com", "mixpanel.com", "segment.io", "segment.com",
        "amplitude.com", "appsflyer.com", "adjust.com", "branch.io", "applovin.com", "mopub.com",
        "moatads.com", "unityads.unity3d.com", "ads.yahoo.com", "advertising.com", "quantserve.com",
        "chartbeat.com", "newrelic.com", "bugsnag.com", "sentry.io", "mgid.com", "popads.net",
        "adsrvr.org", "rubiconproject.com", "pubmatic.com", "openx.net", "smaato.net", "inmobi.com",
    )

    /** Awalan paket SDK pelacak yang umum, untuk pemindai aplikasi. */
    val SDK_PREFIXES: Set<String> = setOf(
        "com.google.firebase.analytics", "com.google.firebase.crashlytics", "com.google.android.gms.analytics",
        "io.sentry", "com.microsoft.appcenter", "com.mixpanel", "com.amplitude", "com.flurry",
        "com.adjust.sdk", "com.appsflyer", "com.onesignal", "com.facebook.appevents", "ch.acra",
        "com.bugsnag", "com.newrelic", "com.applovin", "com.unity3d.ads", "com.mopub",
    )
}
