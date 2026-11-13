// molina-patch13c
package com.molina.terminal.proot

import com.molina.log.MolinaEvents
import com.molina.log.MolinaLogger
import java.io.File
import java.nio.file.Files

/**
 * Peristiwa terstruktur area `proot` untuk persiapan sesi Debian. Memakai id sesi yang sama dengan
 * `term.*` (tag Molina/proot/<id>), sehingga `term.exit-early` bisa dibaca bersama langkah
 * persiapannya. Tidak ada path yang dicatat.
 */
internal object ProotEvents {
    private const val AREA = "proot"

    fun rootfs(logger: MolinaLogger, id: String?, rootfs: File) {
        val readable = rootfs.isDirectory && rootfs.canRead()
        val bash = present(File(rootfs, "usr/bin/bash")) || present(File(rootfs, "bin/bash"))
        val env = present(File(rootfs, "usr/bin/env"))
        val fields = linkedMapOf("readable" to readable.toString(), "bash" to bash.toString(), "env" to env.toString())
        when {
            !readable -> MolinaEvents.bug(AREA, "rootfs-unreadable", "rootfs tidak terbaca", fields = fields, id = id, logger = logger)
            !bash -> MolinaEvents.bug(AREA, "rootfs-no-shell", "bash tidak ditemukan di rootfs", fields = fields, id = id, logger = logger)
            !env -> MolinaEvents.bug(AREA, "rootfs-no-env", "usr/bin/env tidak ditemukan di rootfs", fields = fields, id = id, logger = logger)
            else -> MolinaEvents.behavior(AREA, "rootfs-checked", fields = fields, id = id, logger = logger)
        }
    }

    fun guestFiles(logger: MolinaLogger, id: String?, created: List<String>) =
        MolinaEvents.behavior(
            AREA, "guest-files",
            fields = mapOf("created" to if (created.isEmpty()) "none" else created.joinToString("+")),
            id = id, logger = logger,
        )

    fun launch(logger: MolinaLogger, id: String?, layout: DebianLayout, noSeccomp: Boolean, hasLoader: Boolean, hasLibs: Boolean) {
        val bin = layout.prootBin
        if (!bin.isFile) {
            MolinaEvents.bug(AREA, "binary-missing", "binary proot tidak ditemukan", id = id, logger = logger)
            return
        }
        if (!bin.canExecute()) {
            MolinaEvents.bug(AREA, "binary-not-executable", "binary proot tidak bisa dieksekusi", id = id, logger = logger)
            return
        }
        val arm64 = ElfReader.parseFile(bin)?.isArm64
        val fields = linkedMapOf(
            "arm64" to (arm64?.toString() ?: "unknown"),
            "loader" to hasLoader.toString(),
            "libs" to hasLibs.toString(),
            "seccomp" to if (noSeccomp) "off" else "on",
        )
        if (arm64 != true) {
            MolinaEvents.bug(AREA, "binary-wrong-arch", "binary proot bukan arm64 atau tidak terbaca", fields = fields, id = id, logger = logger)
        } else {
            MolinaEvents.behavior(AREA, "launch-built", fields = fields, id = id, logger = logger)
        }
    }

    private fun present(f: File): Boolean = f.isFile || Files.isSymbolicLink(f.toPath())
}
