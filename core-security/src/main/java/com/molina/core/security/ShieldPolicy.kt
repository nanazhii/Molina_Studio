package com.molina.core.security

import java.net.Inet6Address
import java.net.InetAddress

/**
 * Aturan Shield yang berlaku SETELAH DNS di-resolve. [UrlPolicy] hanya melihat teks URL; di sini
 * alamat IP hasil resolusi diperiksa dengan aturan yang sama, sehingga nama host publik yang
 * diarahkan ke alamat privat atau loopback (DNS rebinding) ikut tertolak. Murni, tanpa Android.
 */
object ShieldPolicy {
    /** Port layanan sensitif yang tidak pernah dibutuhkan lalu lintas web biasa (surel, basis data, SMB, RDP, SSH). */
    private val BLOCKED_PORTS = setOf(
        22, 23, 25, 110, 135, 139, 143, 445, 465, 587, 993, 995,
        1433, 3306, 3389, 5432, 6379, 27017,
    )

    fun portAllowed(port: Int): Boolean = port in 1..65535 && port !in BLOCKED_PORTS

    /** Periksa satu alamat IP hasil resolusi memakai aturan [UrlPolicy] (loopback, link-local, privat, dst.). */
    fun checkAddress(addr: InetAddress, allowLan: Boolean): UrlPolicy.Verdict {
        val lit = (addr.hostAddress ?: return UrlPolicy.Verdict.Blocked(UrlPolicy.Reason.MALFORMED))
            .substringBefore('%')
        val url = if (addr is Inet6Address) "https" + "://[" + lit + "]/" else "https" + "://" + lit + "/"
        return UrlPolicy.check(url, allowLan)
    }
}
