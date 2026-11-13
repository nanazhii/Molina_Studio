// molina-patch10b
package com.molina.terminal.proot

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Letak seluruh berkas Debian PRoot di dalam filesDir aplikasi. */
class DebianLayout(filesDir: File) {
    val rootfsBase = File(filesDir, "rootfs")
    val rootfsDir = File(rootfsBase, "debian")
    val prootDir = File(filesDir, "proot")
    val prootBin = File(prootDir, "proot")
    val prootTmp = File(filesDir, "proot-tmp")

    fun installer(isCancelled: () -> Boolean = { false }) = RootfsInstaller(rootfsBase, isCancelled)

    fun isRootfsInstalled(): Boolean = RootfsInstaller(rootfsBase).isInstalled()

    fun isProotReady(): Boolean =
        prootBin.isFile && prootBin.canExecute() && ElfReader.parseFile(prootBin)?.isArm64 == true

    fun isReady(): Boolean = isRootfsInstalled() && isProotReady()

    /**
     * Salin folder proot buatan pengguna (proot + pustaka + loader) ke filesDir, hanya bila
     * lolos pemeriksaan. /sdcard dipasang noexec, jadi eksekusi harus dari filesDir.
     */
    fun importProot(source: File = File(ProotLocation.EXTERNAL_DIR)): ProotReport {
        val report = ProotInspector.inspect(source)
        if (!report.ok) return report
        val files = source.listFiles { f -> f.isFile } ?: throw IOException("Tidak bisa membaca ${source.path}")
        prootDir.mkdirs()
        for (f in files) {
            val dst = File(prootDir, f.name)
            Files.copy(f.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING)
            dst.setReadable(true, true)
            dst.setExecutable(true, true)
        }
        return report
    }
}
