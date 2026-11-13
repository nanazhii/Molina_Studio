// molina-patch10b
package com.molina.terminal.proot

import java.io.File
import java.nio.file.Files

class ProotLaunch(
    val command: List<String>,
    val env: Map<String, String>,
    val workingDir: String,
)

/** Menyusun perintah proot untuk Debian. Murni: tidak menjalankan apa pun. */
object ProotLauncher {
    const val GUEST_PATH = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
    private const val SDCARD = "/storage/emulated/0"

    fun build(layout: DebianLayout, hostHome: String, noSeccomp: Boolean = false): ProotLaunch {
        layout.prootTmp.mkdirs()
        val command = mutableListOf(
            layout.prootBin.path,
            "--link2symlink", "-0", "--kill-on-exit",
            "-r", layout.rootfsDir.path,
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", "$SDCARD:/sdcard",
            "-w", "/root",
            "/usr/bin/env", "-i",
            "HOME=/root", "USER=root", "LOGNAME=root",
            "TERM=xterm-256color", "LANG=C.UTF-8",
            "PATH=$GUEST_PATH",
            "/bin/bash", "--login",
        )
        val env = linkedMapOf(
            "HOME" to hostHome,
            "TMPDIR" to layout.prootTmp.path,
            "PATH" to "/system/bin:/system/xbin",
            "PROOT_TMP_DIR" to layout.prootTmp.path,
        )
        val loader = File(layout.prootDir, "loader")
        if (loader.isFile) env["PROOT_LOADER"] = loader.path
        if (layout.prootDir.list()?.any { it.contains(".so") } == true) {
            env["LD_LIBRARY_PATH"] = layout.prootDir.path
        }
        if (noSeccomp) env["PROOT_NO_SECCOMP"] = "1"
        return ProotLaunch(command, env, hostHome)
    }

    /** Berkas dasar rootfs (idempotent; tidak menimpa suntingan pengguna). */
    fun prepareGuest(rootfs: File) {
        File(rootfs, "sdcard").mkdirs()
        writeIfMissing(File(rootfs, "etc/resolv.conf"), "nameserver 1.1.1.1\nnameserver 8.8.8.8\n", true)
        writeIfMissing(File(rootfs, "etc/hosts"), "127.0.0.1 localhost\n::1 localhost\n", true)
        writeIfMissing(File(rootfs, "etc/apt/apt.conf.d/01molina-proot"), "APT::Sandbox::User \"root\";\n", false)
    }

    private fun writeIfMissing(f: File, content: String, emptyCountsAsMissing: Boolean) {
        val p = f.toPath()
        val missing = Files.isSymbolicLink(p) || !f.exists() || (emptyCountsAsMissing && f.length() == 0L)
        if (!missing) return
        f.parentFile?.mkdirs()
        Files.deleteIfExists(p)
        f.writeText(content)
    }
}
