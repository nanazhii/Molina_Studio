package com.molina.core.security

/**
 * Pencocok domain pelacak per sufiks (sub.doubleclick.net cocok dengan doubleclick.net).
 * Pengecualian pengguna ([isTracker] parameter allowed) menang atas daftar blokir.
 */
class TrackerBlocklist(private val domains: Set<String> = TrackerDomains.DEFAULT) {

    /** [allowed] harus sudah dinormalkan lewat [normalize]. */
    fun isTracker(host: String, allowed: Set<String> = emptySet()): Boolean {
        val h = normalize(host)
        if (h.isEmpty()) return false
        if (matches(allowed, h)) return false
        return matches(domains, h)
    }

    private fun matches(set: Set<String>, host: String): Boolean {
        if (set.isEmpty()) return false
        var cur = host
        while (true) {
            if (cur in set) return true
            val dot = cur.indexOf('.')
            if (dot < 0) return false
            cur = cur.substring(dot + 1)
        }
    }

    companion object {
        fun normalize(host: String): String = host.trim().lowercase().trimEnd('.')
    }
}
