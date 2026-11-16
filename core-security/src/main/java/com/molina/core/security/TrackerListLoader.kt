package com.molina.core.security

import java.io.File
import java.security.MessageDigest

/**
 * Pemuat daftar pelacak tambahan dari berkas lokal (tanpa jaringan, tanpa server pembaruan).
 * Format: satu domain per baris, atau gaya berkas hosts (`0.0.0.0 domain`); `#` memulai komentar.
 * Baris tidak sah dilewati dan dihitung. Batas ukuran dan jumlah mencegah daftar raksasa menghabiskan memori.
 */
object TrackerListLoader {
    const val MAX_BYTES = 2L * 1024 * 1024
    const val MAX_ENTRIES = 50_000
    private val DOMAIN = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$")
    private val SINKS = setOf("0.0.0.0", "127.0.0.1", "::1")

    class Result(val domains: Set<String>, val rejected: Int, val error: String? = null)

    fun parse(text: String): Result {
        val out = LinkedHashSet<String>()
        var rejected = 0
        for (raw in text.lineSequence()) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val parts = line.split(Regex("\\s+"))
            val candidate = when {
                parts.size == 1 -> parts[0]
                parts.size == 2 && parts[0] in SINKS -> parts[1]
                else -> { rejected++; continue }
            }.lowercase().trimEnd('.')
            if (!DOMAIN.matches(candidate) || candidate.length > 253) { rejected++; continue }
            if (out.size >= MAX_ENTRIES) return Result(out, rejected, "melebihi $MAX_ENTRIES entri, sisanya diabaikan")
            out.add(candidate)
        }
        return Result(out, rejected)
    }

    /** Baca berkas; [expectedSha256] (heks) opsional untuk memastikan berkas tidak berubah. Tidak pernah melempar. */
    fun load(file: File, expectedSha256: String? = null): Result {
        return try {
            if (!file.isFile) return Result(emptySet(), 0, "berkas tidak ada")
            if (file.length() > MAX_BYTES) return Result(emptySet(), 0, "berkas terlalu besar")
            val bytes = file.readBytes()
            if (expectedSha256 != null) {
                val got = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                if (!got.equals(expectedSha256.trim(), ignoreCase = true)) return Result(emptySet(), 0, "SHA-256 tidak cocok")
            }
            parse(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            Result(emptySet(), 0, "gagal membaca: " + e.javaClass.simpleName)
        }
    }

    /** Daftar bawaan digabung dengan tambahan pengguna. */
    fun merged(extra: Set<String>): TrackerBlocklist = TrackerBlocklist(TrackerDomains.DEFAULT + extra)
}
