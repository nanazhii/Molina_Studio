// molina-27q
package com.molina.terminal.proot

import java.io.File

/**
 * [molina-27q] Lingkungan proses untuk menjalankan SATU perintah di Debian PRoot tanpa shell interaktif (mis. 7z dari tab
 * Berkas). Isinya sama dengan env di [ProotLauncher.build]; Terminal tidak memakainya dan perilakunya tidak berubah.
 */
object ProotExec {
    fun env(layout: DebianLayout, hostHome: String, noSeccomp: Boolean, compat: Boolean): Map<String, String> {
        layout.prootTmp.mkdirs()
        val l2s = File(layout.rootfsDir, ".l2s").apply { mkdirs() }
        val env = linkedMapOf(
            "HOME" to hostHome,
            "TMPDIR" to layout.prootTmp.path,
            "PATH" to "/system/bin:/system/xbin",
            "PROOT_TMP_DIR" to layout.prootTmp.path,
            "PROOT_L2S_DIR" to l2s.path,
        )
        val loader = File(layout.prootDir, "loader")
        if (loader.isFile) env["PROOT_LOADER"] = loader.path
        if (layout.prootDir.list()?.any { it.contains(".so") } == true) env["LD_LIBRARY_PATH"] = layout.prootDir.path
        if (noSeccomp || compat) env["PROOT_NO_SECCOMP"] = "1"
        return env
    }
}
