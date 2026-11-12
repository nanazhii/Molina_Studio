package com.molina.terminal.proot

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.GZIPInputStream

sealed interface InstallProgress {
    class Downloading(val bytes: Long, val total: Long) : InstallProgress
    object Verifying : InstallProgress
    class Extracting(val done: Int, val total: Int) : InstallProgress
    object Done : InstallProgress
}

/**
 * Pemasang rootfs Debian: unduh (bisa dilanjutkan) -> verifikasi SHA-256 -> ekstrak ke
 * direktori staging -> pindah atomik. BLOCKING: panggil dari Dispatchers.IO / thread latar.
 * Aman dijalankan berulang: bila sudah terpasang dengan hash yang sama, tidak melakukan apa pun.
 *
 * @param baseDir biasanya File(context.filesDir, "rootfs"); hasil di baseDir/debian.
 */
class RootfsInstaller(
    private val baseDir: File,
    private val isCancelled: () -> Boolean = { false },
) {
    val rootfsDir = File(baseDir, "debian")
    private val marker = File(baseDir, "debian.ok")
    private val archive = File(baseDir, "rootfs.tar.gz")
    private val part = File(baseDir, "rootfs.tar.gz.part")
    private val staging = File(baseDir, "debian.tmp")

    fun isInstalled(): Boolean =
        rootfsDir.isDirectory && marker.isFile && marker.readText().trim() == RootfsPin.SHA256

    fun install(onProgress: (InstallProgress) -> Unit = {}) {
        if (isInstalled()) {
            onProgress(InstallProgress.Done)
            return
        }
        baseDir.mkdirs()
        if (baseDir.usableSpace < RootfsPin.MIN_FREE_BYTES) {
            throw IOException("Ruang penyimpanan kurang: butuh sekitar 350 MB")
        }
        onProgress(InstallProgress.Verifying)
        if (!(archive.isFile && Hashing.sha256Hex(archive) == RootfsPin.SHA256)) {
            archive.delete()
            download(onProgress)
            onProgress(InstallProgress.Verifying)
            if (Hashing.sha256Hex(archive) != RootfsPin.SHA256) {
                archive.delete()
                throw IOException("SHA-256 rootfs tidak cocok; berkas dibuang")
            }
        }
        extract(onProgress)
        marker.writeText(RootfsPin.SHA256 + "\n")
        archive.delete()
        onProgress(InstallProgress.Done)
    }

    /** Hapus rootfs terpasang (aman terhadap symlink). */
    fun uninstall() {
        marker.delete()
        deleteTree(rootfsDir)
        deleteTree(staging)
    }

    private fun download(onProgress: (InstallProgress) -> Unit) {
        val total = RootfsPin.SIZE_BYTES
        var have = if (part.isFile) part.length() else 0L
        if (have > total) {
            part.delete()
            have = 0L
        }
        if (have < total) {
            val conn = URI(RootfsPin.URL).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
            try {
                val append = when (val code = conn.responseCode) {
                    206 -> true
                    200 -> false
                    else -> throw IOException("Unduhan gagal: HTTP $code")
                }
                var done = if (append) have else 0L
                var last = 0L
                val buf = ByteArray(64 * 1024)
                FileOutputStream(part, append).use { out ->
                    conn.inputStream.use { input ->
                        while (true) {
                            if (isCancelled()) throw InstallCancelledException()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val now = System.currentTimeMillis()
                            if (now - last >= 250) {
                                last = now
                                onProgress(InstallProgress.Downloading(done, total))
                            }
                        }
                    }
                }
                onProgress(InstallProgress.Downloading(done, total))
            } finally {
                conn.disconnect()
            }
        }
        if (part.length() != total) {
            throw IOException("Unduhan terpotong (${part.length()}/$total); jalankan lagi untuk melanjutkan")
        }
        if (!part.renameTo(archive)) throw IOException("Gagal memindahkan arsip hasil unduhan")
    }

    private fun extract(onProgress: (InstallProgress) -> Unit) {
        deleteTree(staging)
        if (!staging.mkdirs()) throw IOException("Gagal membuat direktori staging")
        val extractor = TarExtractor(staging, isCancelled)
        GZIPInputStream(BufferedInputStream(FileInputStream(archive), 64 * 1024), 64 * 1024).use { gz ->
            extractor.extract(gz) { n -> if (n % 50 == 0) onProgress(InstallProgress.Extracting(n, RootfsPin.ENTRY_COUNT)) }
        }
        onProgress(InstallProgress.Extracting(extractor.entries, RootfsPin.ENTRY_COUNT))
        val version = File(staging, "etc/debian_version").takeIf { it.isFile }?.readText()?.trim()
        if (version != RootfsPin.DEBIAN_VERSION) {
            throw IOException("Versi Debian tidak sesuai: $version (harus ${RootfsPin.DEBIAN_VERSION})")
        }
        deleteTree(rootfsDir)
        if (!staging.renameTo(rootfsDir)) throw IOException("Gagal memindahkan hasil ekstraksi")
    }

    private fun deleteTree(dir: File) {
        val p = dir.toPath()
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) return
        Files.walkFileTree(p, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }
}
