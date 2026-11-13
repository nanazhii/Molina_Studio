// molina-patch14
package com.molina.terminal.core

/** Penyebab gagal start proot yang dikenali dari keluaran dan waktu hidup sesi. */
enum class SessionFailure { PROOT_ASSERT, EARLY_EXIT }

object SessionFailureDetector {
    const val ASSERT_SIGNATURE = "IS_IN_SYSENTER"

    fun isProot(command: String?): Boolean = command?.substringAfterLast('/') == "proot"

    fun classify(command: String?, code: Int?, uptimeMs: Long, tail: String): SessionFailure? {
        if (!isProot(command) || code == null || code == 0) return null
        if (tail.contains(ASSERT_SIGNATURE)) return SessionFailure.PROOT_ASSERT
        return if (code > 0 && uptimeMs < TermEvents.EARLY_EXIT_MS) SessionFailure.EARLY_EXIT else null
    }

    fun message(failure: SessionFailure, code: Int?): String {
        val why = when (failure) {
            SessionFailure.PROOT_ASSERT -> "Proot berhenti karena ketidakcocokan dengan kernel (seccomp)"
            SessionFailure.EARLY_EXIT -> "Proot gagal start"
        }
        val c = if (code == null) "" else " (kode $code)"
        return "\r\n[$why$c. Ketuk \"Coba mode kompatibilitas\".]\r\n"
    }
}
