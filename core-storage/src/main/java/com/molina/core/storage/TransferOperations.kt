// molina-patch26c
package com.molina.core.storage

import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaError
import com.molina.core.common.MolinaResult
import com.molina.core.common.molinaCatching
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.coroutines.cancellation.CancellationException

/**
 * Salin, pindah, dan hapus massal (diadaptasi dari FileOps repo Molina-Studio). Murni JVM dan
 * blocking: panggil dari dispatcher IO. Nama bentrok di tujuan diberi nomor ("nama (1).ext"),
 * tidak pernah menimpa. Berkas setengah jadi dibersihkan bila gagal atau dibatalkan.
 */
internal object TransferOperations {
    private const val BUFFER_SIZE = 64 * 1024
    private const val REPORT_STEP = 256L * 1024

    /** Dilempar saat dibatalkan; turunan CancellationException agar molinaCatching tidak menelannya. */
    private class Cancelled : CancellationException("dibatalkan")

    private class Ctx(
        val kind: TransferKind,
        val itemCount: Int,
        val bytesTotal: Long,
        val cancel: () -> Boolean,
        val onProgress: (TransferProgress) -> Unit,
    ) {
        var itemIndex = 0
        var bytesDone = 0L
        private var lastReported = -1L

        fun checkCancel() {
            if (cancel()) throw Cancelled()
        }

        fun add(n: Long) {
            bytesDone += n
            if (bytesDone - lastReported >= REPORT_STEP) report()
        }

        fun report() {
            lastReported = bytesDone
            onProgress(TransferProgress(kind, itemIndex, itemCount, bytesDone, bytesTotal))
        }
    }

    private fun fail(kind: ErrorKind, msg: String): MolinaResult.Failure =
        MolinaResult.Failure(MolinaError(kind, msg))

    private fun isLink(f: File): Boolean = try {
        Files.isSymbolicLink(f.toPath())
    } catch (e: Exception) {
        false
    }

    private fun isProtected(target: File, protectedPaths: Set<File>): Boolean {
        val t = target.canonicalFile.path
        return protectedPaths.any {
            val p = it.canonicalFile.path
            p == t || p.startsWith(t + File.separator)
        }
    }

    /** true bila [inner] sama dengan atau berada di dalam [outer] (setelah canonical). */
    private fun isInside(inner: File, outer: File): Boolean {
        val i = inner.canonicalFile.path
        val o = outer.canonicalFile.path
        return i == o || i.startsWith(o + File.separator)
    }

    fun copy(
        sources: List<File>,
        destDir: File,
        cancel: () -> Boolean,
        onProgress: (TransferProgress) -> Unit,
    ): MolinaResult<BatchResult> = run(TransferKind.COPY, sources, destDir, emptySet(), cancel, onProgress)

    fun move(
        sources: List<File>,
        destDir: File,
        protectedPaths: Set<File>,
        cancel: () -> Boolean,
        onProgress: (TransferProgress) -> Unit,
    ): MolinaResult<BatchResult> = run(TransferKind.MOVE, sources, destDir, protectedPaths, cancel, onProgress)

    fun deleteAll(
        targets: List<File>,
        protectedPaths: Set<File>,
        cancel: () -> Boolean,
        onProgress: (TransferProgress) -> Unit,
    ): MolinaResult<BatchResult> = run(TransferKind.DELETE, targets, null, protectedPaths, cancel, onProgress)

    private fun run(
        kind: TransferKind,
        items: List<File>,
        destDir: File?,
        protectedPaths: Set<File>,
        cancel: () -> Boolean,
        onProgress: (TransferProgress) -> Unit,
    ): MolinaResult<BatchResult> {
        if (destDir != null) {
            if (!destDir.isDirectory) return fail(ErrorKind.NOT_FOUND, "Folder tujuan tidak ditemukan")
            if (!destDir.canWrite()) return fail(ErrorKind.PERMISSION_DENIED, "Folder tujuan tidak bisa ditulisi")
        }
        var succeeded = 0
        var failed = 0
        var skipped = 0
        var cancelled = false
        var firstError: MolinaError? = null

        // Pemindaian awal untuk total byte; dibatalkan bila diminta.
        val sizes = LongArray(items.size)
        try {
            for (i in items.indices) {
                if (cancel()) throw Cancelled()
                sizes[i] = sizeOf(items[i], cancel)
            }
        } catch (e: Cancelled) {
            return MolinaResult.Success(BatchResult(kind, 0, 0, 0, true, null))
        }
        val ctx = Ctx(kind, items.size, sizes.sum(), cancel, onProgress)

        for ((i, item) in items.withIndex()) {
            ctx.itemIndex = i
            if (cancel()) {
                cancelled = true
                break
            }
            ctx.report()
            val before = ctx.bytesDone
            val outcome = try {
                when (kind) {
                    TransferKind.COPY -> copyItem(item, destDir!!, ctx)
                    TransferKind.MOVE -> moveItem(item, destDir!!, protectedPaths, sizes[i], ctx)
                    TransferKind.DELETE -> deleteItem(item, protectedPaths, ctx)
                }
            } catch (e: Cancelled) {
                cancelled = true
                break
            }
            when (outcome) {
                is MolinaResult.Success -> if (outcome.value == Outcome.SKIPPED) skipped++ else succeeded++
                is MolinaResult.Failure -> {
                    failed++
                    if (firstError == null) firstError = outcome.error
                }
            }
            // Item gagal/dilewati tetap dihitung selesai pada bilah progres agar total tidak menggantung.
            val spent = ctx.bytesDone - before
            if (spent < sizes[i]) ctx.bytesDone += sizes[i] - spent
        }
        if (!cancelled) {
            ctx.itemIndex = items.size
            ctx.report()
        }
        return MolinaResult.Success(BatchResult(kind, succeeded, failed, skipped, cancelled, firstError))
    }

    private enum class Outcome { DONE, SKIPPED }

    /** Menjalankan [block]; exception I/O dipetakan ke ErrorKind, pembatalan diteruskan. */
    private inline fun guard(block: () -> MolinaResult<Outcome>): MolinaResult<Outcome> =
        when (val r = molinaCatching(block)) {
            is MolinaResult.Success -> r.value
            is MolinaResult.Failure -> r
        }

    private fun done(): MolinaResult<Outcome> = MolinaResult.Success(Outcome.DONE)

    // --- salin

    private fun copyItem(src: File, destDir: File, ctx: Ctx): MolinaResult<Outcome> = guard {
        if (!NameRules.present(src)) return@guard fail(ErrorKind.NOT_FOUND, "Item tidak ditemukan")
        if (!isLink(src) && src.isDirectory && isInside(destDir, src)) {
            return@guard fail(ErrorKind.INVALID_PATH, "Tujuan berada di dalam folder sumber")
        }
        copyGuarded(src, NameRules.unique(destDir, src.name), ctx)
        done()
    }

    /** Menyalin ke [target]; bila gagal atau dibatalkan, hasil sebagian dihapus. */
    private fun copyGuarded(src: File, target: File, ctx: Ctx) {
        try {
            copyRec(src, target, ctx)
        } catch (e: Throwable) {
            deleteQuietly(target)
            throw e
        }
    }

    private fun copyRec(src: File, dst: File, ctx: Ctx) {
        ctx.checkCancel()
        if (isLink(src)) {
            Files.createSymbolicLink(dst.toPath(), Files.readSymbolicLink(src.toPath()))
            return
        }
        if (src.isDirectory) {
            if (!dst.mkdir()) throw IOException("gagal membuat folder")
            val kids = src.listFiles() ?: throw SecurityException()
            for (k in kids.sortedBy { it.name }) copyRec(k, File(dst, k.name), ctx)
            dst.setLastModified(src.lastModified())
        } else {
            copyFile(src, dst, ctx)
        }
    }

    private fun copyFile(src: File, dst: File, ctx: Ctx) {
        src.inputStream().use { input ->
            dst.outputStream().use { output ->
                val buf = ByteArray(BUFFER_SIZE)
                while (true) {
                    ctx.checkCancel()
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    ctx.add(n.toLong())
                }
            }
        }
        dst.setLastModified(src.lastModified())
    }

    // --- pindah

    private fun moveItem(
        src: File,
        destDir: File,
        protectedPaths: Set<File>,
        size: Long,
        ctx: Ctx,
    ): MolinaResult<Outcome> = guard {
        if (!NameRules.present(src)) return@guard fail(ErrorKind.NOT_FOUND, "Item tidak ditemukan")
        if (isProtected(src, protectedPaths)) return@guard fail(ErrorKind.PROTECTED, "Folder ini dilindungi")
        if (!isLink(src) && src.isDirectory && isInside(destDir, src)) {
            return@guard fail(ErrorKind.INVALID_PATH, "Tujuan berada di dalam folder sumber")
        }
        val parent = src.parentFile
        if (parent != null && parent.canonicalFile == destDir.canonicalFile) {
            return@guard MolinaResult.Success(Outcome.SKIPPED)
        }
        val target = NameRules.unique(destDir, src.name)
        if (src.renameTo(target)) {
            ctx.add(size)
            return@guard done()
        }
        // rename gagal (mis. lintas volume): salin lalu hapus sumber hanya bila salinan utuh.
        copyGuarded(src, target, ctx)
        if (!deleteRec(src, ctx)) throw IOException("gagal menghapus sumber setelah menyalin")
        done()
    }

    // --- hapus

    private fun deleteItem(target: File, protectedPaths: Set<File>, ctx: Ctx): MolinaResult<Outcome> = guard {
        if (!NameRules.present(target)) return@guard fail(ErrorKind.NOT_FOUND, "Item tidak ditemukan")
        if (isProtected(target, protectedPaths)) return@guard fail(ErrorKind.PROTECTED, "Folder ini dilindungi")
        if (!deleteRec(target, ctx)) throw IOException("gagal menghapus")
        done()
    }

    private fun deleteRec(f: File, ctx: Ctx): Boolean {
        ctx.checkCancel()
        if (!isLink(f) && f.isDirectory) {
            val kids = f.listFiles()
            if (kids != null) for (k in kids) if (!deleteRec(k, ctx)) return false
            return f.delete()
        }
        val len = if (isLink(f)) 0L else f.length()
        val ok = f.delete()
        if (ok) ctx.add(len)
        return ok
    }

    private fun deleteQuietly(f: File) {
        try {
            if (!NameRules.present(f)) return
            if (!isLink(f) && f.isDirectory) f.listFiles()?.forEach { deleteQuietly(it) }
            f.delete()
        } catch (e: Exception) {
            // pembersihan sebisanya
        }
    }

    private fun sizeOf(f: File, cancel: () -> Boolean): Long {
        if (cancel()) throw Cancelled()
        if (isLink(f)) return 0L
        if (!f.isDirectory) return f.length()
        var sum = 0L
        f.listFiles()?.forEach { sum += sizeOf(it, cancel) }
        return sum
    }
}
