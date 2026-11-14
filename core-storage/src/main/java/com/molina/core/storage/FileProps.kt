// molina-patch26j
package com.molina.core.storage

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/**
 * Properti satu item. [path] relatif terhadap root ("" = root). Untuk folder, [sizeBytes], [fileCount],
 * dan [folderCount] dihitung rekursif (tautan dihitung sebagai berkas dan tidak diikuti).
 */
data class FileProps(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val fileCount: Int,
    val folderCount: Int,
    val lastModifiedMs: Long,
    val canRead: Boolean,
    val canWrite: Boolean,
    val isSymlink: Boolean,
)

/** Berkas yang boleh dibagikan. [skipped]: item yang dilewati (folder atau isi Sampah). */
data class ShareTargets(val files: List<File>, val skipped: Int)

/** Pembaca properti (port FileOps.props/sha256 repo lama, JVM murni). Blocking: panggil dari dispatcher IO. */
internal object PropsReader {
    /** Turunan CancellationException agar molinaCatching tidak menelannya. */
    private class Cancelled : CancellationException("dibatalkan")

    private fun isLink(f: File): Boolean = try {
        Files.isSymbolicLink(f.toPath())
    } catch (e: Exception) {
        false
    }

    fun read(f: File, relative: String, isCancelled: () -> Boolean): FileProps {
        val link = isLink(f)
        val dir = f.isDirectory && !link
        var size = 0L
        var files = 0
        var folders = 0
        if (dir) {
            val stack = ArrayDeque<File>()
            stack.addLast(f)
            while (stack.isNotEmpty()) {
                if (isCancelled()) throw Cancelled()
                val kids = stack.removeLast().listFiles() ?: continue
                for (k in kids) {
                    if (isLink(k)) {
                        files++
                    } else if (k.isDirectory) {
                        folders++
                        stack.addLast(k)
                    } else {
                        files++
                        size += k.length()
                    }
                }
            }
        } else {
            size = f.length()
        }
        return FileProps(f.name, relative, dir, size, files, folders, f.lastModified(), f.canRead(), f.canWrite(), link)
    }

    /** SHA-256 heksadesimal isi [f], dibaca bertahap; [onProgress] menerima (selesai, total) byte. */
    fun sha256(f: File, isCancelled: () -> Boolean, onProgress: (Long, Long) -> Unit): String {
        if (!f.isFile) throw IOException("bukan berkas biasa")
        val total = f.length()
        val md = MessageDigest.getInstance("SHA-256")
        var done = 0L
        f.inputStream().use { i ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                if (isCancelled()) throw Cancelled()
                val n = i.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
                done += n
                onProgress(done, total)
            }
        }
        return md.digest().joinToString("") { String.format(Locale.ROOT, "%02x", it) }
    }
}
