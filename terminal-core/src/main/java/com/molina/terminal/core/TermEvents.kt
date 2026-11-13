// molina-patch13b
package com.molina.terminal.core

import com.molina.log.MolinaEvents
import com.molina.log.MolinaLogger
import java.util.concurrent.atomic.AtomicInteger

/** Id sesi berurutan (t1, t2, ...) agar semua peristiwa satu sesi bisa dikorelasikan lewat tag. */
object TerminalSessionIds {
    private val counter = AtomicInteger()
    fun next(): String = "t" + counter.incrementAndGet()
}

/**
 * Peristiwa terstruktur area `term` (kode otomatis berawalan `term.`). Isi input/output terminal
 * dan environment TIDAK pernah dicatat.
 */
internal object TermEvents {
    private const val AREA = "term"

    /** Proses yang berakhir lebih cepat dari ini dengan kode non-nol dianggap gagal start. */
    const val EARLY_EXIT_MS = 5_000L

    fun start(logger: MolinaLogger, id: String, pid: Int, command: String?, cols: Int, rows: Int) =
        MolinaEvents.behavior(
            AREA, "pty-start",
            fields = mapOf(
                "pid" to pid.toString(),
                "cmd" to (command?.substringAfterLast('/')?.ifEmpty { null } ?: "unknown"),
                "cols" to cols.toString(),
                "rows" to rows.toString(),
            ),
            id = id, logger = logger,
        )

    fun resize(logger: MolinaLogger, id: String, cols: Int, rows: Int) =
        MolinaEvents.behavior(AREA, "resize", fields = mapOf("cols" to cols.toString(), "rows" to rows.toString()), id = id, logger = logger)

    fun close(logger: MolinaLogger, id: String, running: Boolean) =
        MolinaEvents.behavior(AREA, "close", fields = mapOf("running" to running.toString()), id = id, logger = logger)

    /** Hanya PROOT_ASSERT yang mendapat peristiwa sendiri; EARLY_EXIT sudah tercatat sebagai exit-early. */
    fun prootFailure(logger: MolinaLogger, id: String, failure: SessionFailure, code: Int?, uptimeMs: Long) {
        if (failure != SessionFailure.PROOT_ASSERT) return
        MolinaEvents.bug(
            AREA, "proot-assert", "proot abort (assertion IS_IN_SYSENTER), kemungkinan seccomp/kernel lama",
            fields = mapOf("code" to (code?.toString() ?: "unknown"), "uptime_ms" to uptimeMs.coerceAtLeast(0L).toString()),
            id = id, logger = logger,
        )
    }

    fun spawnFailed(logger: MolinaLogger, id: String, t: Throwable?) {
        val linkage = t is LinkageError
        MolinaEvents.bug(
            AREA, if (linkage) "native-missing" else "spawn-failed",
            t?.message ?: "penyebab tidak diketahui", t,
            fields = mapOf("error" to (t?.javaClass?.simpleName?.ifEmpty { null } ?: "unknown")),
            id = id, logger = logger,
        )
    }

    /**
     * Klasifikasi keluar proses:
     *  - null: kode tidak diketahui (BUG); negatif: dihentikan sinyal, mis. SIGSEGV/SIGKILL (BUG)
     *  - 0: normal (BEHAV); non-nol sebelum [EARLY_EXIT_MS]: gagal start, mis. proot/exec (BUG)
     *  - non-nol sesudahnya: biasanya `exit N` pengguna setelah perintah gagal (BEHAV, bukan bug)
     */
    fun exit(logger: MolinaLogger, id: String, code: Int?, uptimeMs: Long) {
        val up = uptimeMs.coerceAtLeast(0L).toString()
        when {
            code == null -> MolinaEvents.bug(AREA, "exit-unknown", "kode keluar tidak diketahui", fields = mapOf("uptime_ms" to up), id = id, logger = logger)
            code < 0 -> MolinaEvents.bug(AREA, "killed", "proses dihentikan sinyal", fields = mapOf("signal" to (-code).toString(), "uptime_ms" to up), id = id, logger = logger)
            code > 0 && uptimeMs < EARLY_EXIT_MS -> MolinaEvents.bug(
                AREA, "exit-early", "proses keluar sebelum ${EARLY_EXIT_MS / 1000} detik",
                fields = mapOf("code" to code.toString(), "uptime_ms" to up), id = id, logger = logger,
            )
            else -> MolinaEvents.behavior(AREA, "exit", fields = mapOf("code" to code.toString(), "uptime_ms" to up), id = id, logger = logger)
        }
    }
}
