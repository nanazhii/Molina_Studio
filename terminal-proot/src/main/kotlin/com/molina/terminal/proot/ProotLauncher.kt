// molina-patch10b
package com.molina.terminal.proot

import com.molina.log.MolinaLog
import com.molina.log.MolinaLogger
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

class ProotLaunch(
    val command: List<String>,
    val env: Map<String, String>,
    val workingDir: String,
)

/** Menyusun perintah proot untuk Debian. Tidak menjalankan proses; hanya mencatat BEHAV/BUG pemeriksaan (tanpa path). */
object ProotLauncher {
    const val GUEST_PATH = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
    private const val SDCARD = "/storage/emulated/0"

    fun build(
        layout: DebianLayout,
        hostHome: String,
        noSeccomp: Boolean = false,
        id: String? = null,
        logger: MolinaLogger = MolinaLog.logger,
        lexists: (String) -> Boolean = { p -> hostLexists(p) },
        compat: Boolean = false,
    ): ProotLaunch {
        layout.prootTmp.mkdirs()
        val command = mutableListOf(
            layout.prootBin.path,
            "--link2symlink", "-0", "--kill-on-exit",
            *(if (compat) emptyArray<String>() else arrayOf("--sysvipc")),
            "-r", layout.rootfsDir.path,
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", "$SDCARD:/sdcard",
            *guestBinds(layout, lexists).toTypedArray(),
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
            "PROOT_L2S_DIR" to l2sDir(layout),
        )
        val loader = File(layout.prootDir, "loader")
        if (loader.isFile) env["PROOT_LOADER"] = loader.path
        if (layout.prootDir.list()?.any { it.contains(".so") } == true) {
            env["LD_LIBRARY_PATH"] = layout.prootDir.path
        }
        if (noSeccomp || compat) env["PROOT_NO_SECCOMP"] = "1"
        ProotEvents.launch(logger, id, layout, noSeccomp || compat, loader.isFile, env.containsKey("LD_LIBRARY_PATH"))
        return ProotLaunch(command, env, hostHome)
    }

    private fun hostLexists(path: String): Boolean = Files.exists(File(path).toPath(), LinkOption.NOFOLLOW_LINKS)

    /** Folder tempat --link2symlink menaruh berkas pengganti hard link (mengikuti proot-distro). */
    private fun l2sDir(layout: DebianLayout): String {
        val d = File(layout.rootfsDir, ".l2s")
        d.mkdirs()
        return d.path
    }

    /** Bind tambahan ala proot-distro: /dev/random, /dev/fd, stdin/stdout/stderr, dan /dev/shm milik aplikasi. */
    private fun guestBinds(layout: DebianLayout, lexists: (String) -> Boolean): List<String> {
        val out = ArrayList<String>()
        if (lexists("/dev/urandom")) out += listOf("-b", "/dev/urandom:/dev/random")
        if (!lexists("/dev/fd") && lexists("/proc/self/fd")) out += listOf("-b", "/proc/self/fd:/dev/fd")
        for ((i, name) in listOf("stdin", "stdout", "stderr").withIndex()) {
            if (!lexists("/dev/$name") && lexists("/proc/self/fd/$i")) out += listOf("-b", "/proc/self/fd/$i:/dev/$name")
        }
        if (layout.prootShm.isDirectory || layout.prootShm.mkdirs()) out += listOf("-b", "${layout.prootShm.path}:/dev/shm")
        return out
    }

    /** Berkas dasar rootfs (idempotent; tidak menimpa suntingan pengguna). */
    fun prepareGuest(rootfs: File, id: String? = null, logger: MolinaLogger = MolinaLog.logger) {
        ProotEvents.rootfs(logger, id, rootfs)
        File(rootfs, "sdcard").mkdirs()
        val created = ArrayList<String>()
        if (writeIfMissing(File(rootfs, "etc/resolv.conf"), "nameserver 1.1.1.1\nnameserver 8.8.8.8\n", true)) created += "resolv.conf"
        if (writeIfMissing(File(rootfs, "etc/hosts"), "127.0.0.1 localhost\n::1 localhost\n", true)) created += "hosts"
        if (writeIfMissing(File(rootfs, "etc/apt/apt.conf.d/01molina-proot"), "APT::Sandbox::User \"root\";\n", false)) created += "apt-conf"
        ProotEvents.guestFiles(logger, id, created)
    }

    /** true bila berkas ditulis; false bila sudah ada (suntingan pengguna tidak ditimpa). */
    private fun writeIfMissing(f: File, content: String, emptyCountsAsMissing: Boolean): Boolean {
        val p = f.toPath()
        val missing = Files.isSymbolicLink(p) || !f.exists() || (emptyCountsAsMissing && f.length() == 0L)
        if (!missing) return false
        f.parentFile?.mkdirs()
        Files.deleteIfExists(p)
        f.writeText(content)
        return true
    }
}
