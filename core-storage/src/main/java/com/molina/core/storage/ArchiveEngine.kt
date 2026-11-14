// molina-patch26k
package com.molina.core.storage

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.tukaani.xz.LZMA2Options
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import kotlin.coroutines.cancellation.CancellationException

/** Dilempar saat dibatalkan; turunan CancellationException agar tidak tertelan penangkap umum. */
internal class ArchiveCancelled : CancellationException("dibatalkan")

/** Kemajuan dan pembatalan satu operasi arsip. Dipakai dari satu thread IO. */
internal class ArchiveCtl(
    private val kind: ArchiveKind,
    private val cancel: () -> Boolean,
    private val onProgress: (ArchiveProgress) -> Unit,
) {
    var total = 0L
    var done = 0L
        private set
    private var lastReported = -1L

    fun check() {
        if (cancel()) throw ArchiveCancelled()
    }

    fun add(n: Long) {
        done += n
        if (done - lastReported >= REPORT_STEP) report()
    }

    fun report() {
        lastReported = done
        onProgress(ArchiveProgress(kind, done, total))
    }

    private companion object {
        const val REPORT_STEP = 256L * 1024
    }
}

internal class ExtractOutcome(val dir: File?, val files: Int, val renamed: Int, val skipped: Int, val cancelled: Boolean)

internal class CompressOutcome(val file: File?, val entries: Int, val skipped: Int, val cancelled: Boolean)

/**
 * Mesin arsip Molina (JVM murni, blocking: panggil dari dispatcher IO). ZIP lewat zip4j (termasuk AES);
 * 7z dan tar (gz/bz2/xz) lewat commons-compress + xz. Pola desain mengikuti ArchiveEngine repo lama,
 * kodenya ditulis ulang dengan perbaikan: pembatalan sampai tengah berkas, rollback termasuk folder,
 * batas memori 7z/xz, pagar entri dan ruang kosong, daftar isi bisa dibatalkan, galat tidak menyesatkan.
 * Semua nama entri tidak dipercaya; berkas yang sudah ada tidak pernah ditimpa.
 */
internal object ArchiveEngine {
    private const val IO_BUFFER = 128 * 1024
    private const val SPACE_CHECK_EVERY = 4L * 1024 * 1024

    /** Batas jumlah entri ekstrak; diubah hanya oleh tes. */
    @Volatile
    internal var maxEntries: Int = ArchiveEngineLimits.MAX_ENTRIES

    private fun isLink(f: File): Boolean = try {
        Files.isSymbolicLink(f.toPath())
    } catch (e: Exception) {
        false
    }

    private fun fail(reason: ArchiveFailure, msg: String, cause: Throwable? = null) = ArchiveException(reason, msg, cause)

    private fun requireArchive(file: File): ArchiveFormat {
        if (!file.isFile) throw fail(ArchiveFailure.NOT_FOUND, "Berkas arsip tidak ditemukan")
        if (file.length() == 0L) throw fail(ArchiveFailure.CORRUPT, "Berkas arsip kosong (0 byte)")
        return ArchiveNames.detect(file.name) ?: throw fail(ArchiveFailure.UNSUPPORTED, "Format arsip tidak didukung")
    }

    /** Batas memori dekoder 7z/xz: seperempat heap maksimum, antara 16 MB dan 512 MB. */
    private fun memoryLimitKb(): Int {
        val quarter = Runtime.getRuntime().maxMemory() / 1024L / 4L
        return quarter.coerceIn(16L * 1024L, 512L * 1024L).toInt()
    }

    private fun causeChain(e: Throwable): Sequence<Throwable> = generateSequence(e) { it.cause }.take(6)

    private fun isMemoryLimit(e: Throwable): Boolean = causeChain(e).any { it.javaClass.simpleName == "MemoryLimitException" }

    private fun isNoSpace(e: Throwable): Boolean = causeChain(e).any {
        val m = it.message.orEmpty()
        m.contains("No space left", ignoreCase = true) || m.contains("ENOSPC")
    }

    private fun mapReadError(e: Exception, fmt: ArchiveFormat, pw: String?): ArchiveException {
        if (e is ArchiveException) return e
        if (e is PasswordRequiredException) return fail(ArchiveFailure.NEED_PASSWORD, "Arsip butuh kata sandi")
        if (e is ZipException) {
            val t = e.type?.name
            if (t == "WRONG_PASSWORD" || (t == "CHECKSUM_MISMATCH" && !pw.isNullOrEmpty())) {
                return fail(ArchiveFailure.WRONG_PASSWORD, "Kata sandi salah")
            }
            return fail(ArchiveFailure.CORRUPT, "Arsip rusak atau tidak lengkap", e)
        }
        if (isMemoryLimit(e)) return fail(ArchiveFailure.OUT_OF_MEMORY, "Arsip ini butuh memori lebih besar dari yang tersedia", e)
        if (isNoSpace(e)) return fail(ArchiveFailure.NO_SPACE, "Ruang penyimpanan tidak cukup", e)
        if (fmt == ArchiveFormat.SEVEN_Z && !pw.isNullOrEmpty()) {
            return fail(ArchiveFailure.WRONG_PASSWORD, "Kata sandi salah, atau arsip rusak", e)
        }
        return fail(ArchiveFailure.CORRUPT, "Arsip rusak, tidak lengkap, atau formatnya tidak didukung", e)
    }

    // ------------------------------------------------------------------ daftar isi

    private class ListAcc(val format: ArchiveFormat) {
        val items = ArrayList<ArchiveEntryInfo>()
        var files = 0
        var folders = 0
        var total = 0L
        var enc = false
        var truncated = false

        fun add(name: String, size: Long, dir: Boolean) {
            if (dir) {
                folders++
            } else {
                files++
                if (size > 0) total += size
            }
            if (items.size < ArchiveEngineLimits.LIST_MAX) items.add(ArchiveEntryInfo(name, size, dir)) else truncated = true
        }

        fun result() = ArchiveListing(format, items, files, folders, total, enc, truncated)
    }

    /**
     * Daftar isi tanpa mengekstrak. [cancel] dicek selama membaca aliran; pembatalan melempar CancellationException.
     * 7z yang namanya ikut dienkripsi tanpa [password] melempar NEED_PASSWORD.
     */
    fun list(file: File, password: String?, cancel: () -> Boolean): ArchiveListing {
        val fmt = requireArchive(file)
        val ctl = ArchiveCtl(ArchiveKind.EXTRACT, cancel) { }
        try {
            return when {
                fmt == ArchiveFormat.ZIP -> listZip(file)
                fmt == ArchiveFormat.SEVEN_Z -> list7z(file, password)
                fmt.isTar -> listTar(file, fmt, ctl)
                else -> listSingle(file, fmt, ctl)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            throw fail(ArchiveFailure.OUT_OF_MEMORY, "Memori tidak cukup untuk membuka arsip ini")
        } catch (e: Exception) {
            throw mapReadError(e, fmt, password)
        }
    }

    private fun listZip(file: File): ArchiveListing {
        val acc = ListAcc(ArchiveFormat.ZIP)
        val zf = ZipFile(file)
        if (!zf.isValidZipFile) throw fail(ArchiveFailure.CORRUPT, "Arsip rusak atau tidak lengkap")
        for (h in zf.fileHeaders) {
            if (h.isEncrypted) acc.enc = true
            acc.add(if (h.isDirectory) h.fileName.trimEnd('/') + "/" else h.fileName, h.uncompressedSize, h.isDirectory)
        }
        return acc.result()
    }

    private fun list7z(file: File, password: String?): ArchiveListing {
        val acc = ListAcc(ArchiveFormat.SEVEN_Z)
        open7z(file, password).use { sz ->
            for (en in sz.entries) {
                if (en.isAntiItem) continue
                acc.add(if (en.isDirectory) en.name.trimEnd('/') + "/" else en.name, en.size, en.isDirectory)
            }
            // Isi baru terdeteksi terenkripsi saat dekode: baca 1 byte entri pertama yang punya data
            if (password.isNullOrEmpty()) acc.enc = probeEncrypted7z(sz)
        }
        return acc.result()
    }

    private fun probeEncrypted7z(sz: SevenZFile): Boolean {
        try {
            while (true) {
                val en = sz.nextEntry ?: return false
                if (en.isAntiItem || en.isDirectory || !en.hasStream()) continue
                sz.getInputStream(en).use { it.read() }
                return false
            }
        } catch (e: PasswordRequiredException) {
            return true
        } catch (e: Exception) {
            return false // rusak atau tak terbaca: biarkan ekstrak yang melaporkan
        }
    }

    private fun listTar(file: File, fmt: ArchiveFormat, ctl: ArchiveCtl): ArchiveListing {
        val acc = ListAcc(fmt)
        openTar(file, fmt, ctl).use { tin ->
            while (true) {
                val en = tin.nextEntry ?: break
                if (en.isSymbolicLink || en.isLink) continue
                acc.add(if (en.isDirectory) en.name.trimEnd('/') + "/" else en.name, en.size, en.isDirectory)
                if (acc.truncated) break // daftar penuh: berhenti, tidak membaca tar raksasa sampai habis
            }
        }
        return acc.result()
    }

    private fun listSingle(file: File, fmt: ArchiveFormat, ctl: ArchiveCtl): ArchiveListing {
        val acc = ListAcc(fmt)
        openStream(file, fmt, ctl).use { it.read() } // uji kepala berkas valid
        acc.add(ArchiveNames.stem(file.name), 0L, false)
        return acc.result()
    }

    // ------------------------------------------------------------------ ekstrak

    /** Penulis hasil: memvalidasi nama, menjaga jalur di dalam [base], tidak menimpa, memeriksa ruang kosong. */
    private class Sink(val base: File, val only: Set<String>?, val ctl: ArchiveCtl, val limit: Int) {
        private val createdFiles = ArrayList<File>()
        private val createdDirs = ArrayList<File>()
        var files = 0
        var renamed = 0
        var skipped = 0
        var dirs = 0
        private var entries = 0
        private var sinceCheck = 0L

        /** Nama bersih bila entri dipilih; null bila dilewati. Nama tidak aman melempar [ArchiveException]. */
        fun accept(raw: String, isDir: Boolean): String? {
            entries++
            if (entries > limit) throw ArchiveException(ArchiveFailure.TOO_MANY_ENTRIES, "Arsip memuat terlalu banyak entri")
            val clean = ArchiveNames.cleanEntry(raw)
                ?: throw ArchiveException(ArchiveFailure.UNSAFE_ENTRY, "Entri arsip tidak aman")
            if (clean.isEmpty()) return null
            if (ArchiveNames.segmentTooLong(clean)) {
                throw ArchiveException(ArchiveFailure.NAME_TOO_LONG, "Nama entri arsip terlalu panjang")
            }
            return if (ArchiveNames.selected(clean, isDir, only)) clean else null
        }

        private fun ensureDir(d: File) {
            if (d.isDirectory) return
            val missing = ArrayList<File>()
            var p: File? = d
            while (p != null && !p.isDirectory) {
                missing.add(p)
                p = p.parentFile
            }
            if (!d.mkdirs() && !d.isDirectory) throw ArchiveException(ArchiveFailure.IO, "Gagal membuat folder hasil")
            createdDirs.addAll(missing)
        }

        fun dir(clean: String) {
            val d = ArchiveNames.safeTarget(base, clean)
                ?: throw ArchiveException(ArchiveFailure.UNSAFE_ENTRY, "Entri arsip tidak aman")
            ensureDir(d)
            dirs++
        }

        /** Tentukan berkas keluaran (nama unik bila bentrok) dan catat agar bisa dibatalkan. */
        fun reserve(clean: String): File {
            var out = ArchiveNames.safeTarget(base, clean)
                ?: throw ArchiveException(ArchiveFailure.UNSAFE_ENTRY, "Entri arsip tidak aman")
            val parent = out.parentFile ?: throw ArchiveException(ArchiveFailure.UNSAFE_ENTRY, "Entri arsip tidak aman")
            ensureDir(parent)
            if (NameRules.present(out)) {
                out = NameRules.unique(parent, out.name)
                renamed++
            }
            createdFiles.add(out)
            files++
            return out
        }

        /**
         * Tulis isi [src] ke entri [clean]. [declared] = ukuran di header (-1 = tidak diketahui): isi yang melebihinya
         * dianggap arsip rusak/berbahaya. [byteProgress]: laporkan byte keluaran (tar/gz melaporkan byte terkompresi).
         */
        fun write(clean: String, mtime: Long, src: InputStream, declared: Long, byteProgress: Boolean) {
            val out = reserve(clean)
            var entryBytes = 0L
            try {
                out.outputStream().buffered(64 * 1024).use { o ->
                    val buf = ByteArray(IO_BUFFER)
                    while (true) {
                        ctl.check()
                        val n = src.read(buf)
                        if (n < 0) break
                        entryBytes += n
                        if (declared >= 0 && entryBytes > declared) {
                            throw ArchiveException(ArchiveFailure.CORRUPT, "Isi entri melebihi ukuran di header arsip")
                        }
                        o.write(buf, 0, n)
                        if (byteProgress) ctl.add(n.toLong())
                        sinceCheck += n
                        if (sinceCheck >= SPACE_CHECK_EVERY) {
                            sinceCheck = 0
                            if (DiskSpace.lacks(base, 0L)) throw ArchiveException(ArchiveFailure.NO_SPACE, "Ruang penyimpanan tidak cukup")
                        }
                    }
                }
            } catch (e: java.io.FileNotFoundException) {
                throw ArchiveException(ArchiveFailure.IO, "Tidak bisa menulis berkas hasil", e)
            }
            if (mtime > 0) out.setLastModified(mtime)
        }

        /** Hapus berkas yang dibuat operasi ini, lalu folder yang dibuatnya bila sudah kosong. */
        fun rollback() {
            for (f in createdFiles.asReversed()) f.delete()
            for (d in createdDirs.sortedByDescending { it.path.length }) d.delete()
        }
    }

    /** Ekstrak [file] ke [destDir]. Gagal atau dibatalkan = semua hasil operasi ini dihapus. */
    fun extract(
        file: File,
        destDir: File,
        target: ExtractTarget,
        password: String?,
        only: Set<String>?,
        ctl: ArchiveCtl,
    ): ExtractOutcome {
        val fmt = requireArchive(file)
        if (!destDir.isDirectory || !destDir.canWrite()) throw fail(ArchiveFailure.IO, "Folder tujuan tidak bisa ditulisi")
        val named = target == ExtractTarget.NAMED_FOLDER
        val folder = ArchiveNames.folderName(file.name)
        if (named && ArchiveNames.utf8Size(folder) + 8 > NameRules.MAX_BYTES) {
            throw fail(ArchiveFailure.NAME_TOO_LONG, "Nama arsip terlalu panjang untuk dijadikan folder")
        }
        val base = if (named) ArchiveNames.uniqueFolder(destDir, folder) else destDir
        if (named && !base.mkdirs()) throw fail(ArchiveFailure.IO, "Gagal membuat folder hasil")
        val sink = Sink(base, only, ctl, maxEntries)

        fun undo() {
            if (named) base.deleteRecursively() else sink.rollback()
        }
        try {
            when {
                fmt == ArchiveFormat.ZIP -> extractZip(file, sink, password)
                fmt == ArchiveFormat.SEVEN_Z -> extract7z(file, sink, password)
                fmt.isTar -> extractTar(file, fmt, sink)
                else -> extractSingle(file, fmt, sink)
            }
            if (only != null && sink.files == 0 && sink.dirs == 0) {
                throw fail(ArchiveFailure.NO_MATCH, "Tidak ada entri terpilih yang cocok di arsip")
            }
        } catch (e: ArchiveCancelled) {
            undo()
            return ExtractOutcome(null, 0, 0, 0, true)
        } catch (e: OutOfMemoryError) {
            undo()
            throw fail(ArchiveFailure.OUT_OF_MEMORY, "Memori tidak cukup untuk mengekstrak arsip ini")
        } catch (e: Exception) {
            undo()
            throw mapReadError(e, fmt, password)
        }
        ctl.report()
        return ExtractOutcome(base, sink.files, sink.renamed, sink.skipped, false)
    }

    private fun checkDeclaredSpace(base: File, total: Long) {
        if (total > 0 && DiskSpace.lacks(base, total)) {
            throw fail(ArchiveFailure.NO_SPACE, "Ruang penyimpanan tidak cukup")
        }
    }

    private fun extractZip(file: File, sink: Sink, pw: String?) {
        val ctl = sink.ctl
        val zf = ZipFile(file)
        if (!zf.isValidZipFile) throw fail(ArchiveFailure.CORRUPT, "Arsip rusak atau tidak lengkap")
        val headers = zf.fileHeaders
        if (headers.any { it.isEncrypted }) {
            if (pw.isNullOrEmpty()) throw fail(ArchiveFailure.NEED_PASSWORD, "Arsip butuh kata sandi")
            zf.setPassword(pw.toCharArray())
        }
        val picks = ArrayList<Pair<FileHeader, String>>()
        for (h in headers) {
            val clean = sink.accept(h.fileName, h.isDirectory) ?: continue
            if (h.isDirectory) sink.dir(clean) else picks.add(Pair(h, clean))
        }
        ctl.total = picks.sumOf { it.first.uncompressedSize.coerceAtLeast(0L) }
        checkDeclaredSpace(sink.base, ctl.total)
        for ((h, clean) in picks) {
            ctl.check()
            zf.getInputStream(h).use { sink.write(clean, h.lastModifiedTimeEpoch, it, h.uncompressedSize, true) }
        }
    }

    private fun extract7z(file: File, sink: Sink, pw: String?) {
        val ctl = sink.ctl
        open7z(file, pw).use { sz ->
            var total = 0L
            for (en in sz.entries) {
                if (en.isDirectory || en.isAntiItem || !en.hasStream()) continue
                val c = ArchiveNames.cleanEntry(en.name)
                if (c != null && c.isNotEmpty() && ArchiveNames.selected(c, false, sink.only)) total += en.size.coerceAtLeast(0L)
            }
            ctl.total = total
            checkDeclaredSpace(sink.base, total)
            while (true) {
                val en = sz.nextEntry ?: break
                ctl.check()
                if (en.isAntiItem) continue
                val clean = sink.accept(en.name, en.isDirectory) ?: continue
                if (en.isDirectory) {
                    sink.dir(clean)
                    continue
                }
                val mt = if (en.hasLastModifiedDate) en.lastModifiedDate.time else 0L
                val src: InputStream = if (en.hasStream()) sz.getInputStream(en) else ByteArrayInputStream(ByteArray(0))
                sink.write(clean, mt, src, en.size, true)
            }
        }
    }

    private fun extractTar(file: File, fmt: ArchiveFormat, sink: Sink) {
        sink.ctl.total = file.length() // kemajuan = byte terkompresi yang sudah terbaca
        openTar(file, fmt, sink.ctl).use { tin ->
            while (true) {
                val en = tin.nextEntry ?: break
                sink.ctl.check()
                if (en.isSymbolicLink || en.isLink || (!en.isDirectory && !en.isFile)) {
                    sink.skipped++ // tautan dan berkas khusus tidak pernah dibuat
                    continue
                }
                val clean = sink.accept(en.name, en.isDirectory) ?: continue
                if (en.isDirectory) sink.dir(clean) else sink.write(clean, en.modTime.time, tin, -1L, false)
            }
        }
    }

    private fun extractSingle(file: File, fmt: ArchiveFormat, sink: Sink) {
        sink.ctl.total = file.length()
        openStream(file, fmt, sink.ctl).use { ins ->
            val clean = sink.accept(ArchiveNames.stem(file.name), false) ?: return
            sink.write(clean, 0L, ins, -1L, false)
        }
    }

    // ------------------------------------------------------------------ pembuka aliran

    /** Pembungkus yang melaporkan byte mentah terbaca ke [ctl] (kemajuan untuk aliran terkompresi) dan memeriksa pembatalan. */
    private class Counting(i: InputStream, private val ctl: ArchiveCtl) : FilterInputStream(i) {
        override fun read(): Int {
            ctl.check()
            val b = super.read()
            if (b >= 0) ctl.add(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            ctl.check()
            val n = super.read(b, off, len)
            if (n > 0) ctl.add(n.toLong())
            return n
        }

        override fun skip(n: Long): Long {
            ctl.check()
            val s = super.skip(n)
            if (s > 0) ctl.add(s)
            return s
        }
    }

    private fun decompress(buf: InputStream, fmt: ArchiveFormat): InputStream = when (fmt) {
        ArchiveFormat.TAR_GZ, ArchiveFormat.GZ -> GzipCompressorInputStream(buf)
        ArchiveFormat.TAR_BZ2, ArchiveFormat.BZ2 -> BZip2CompressorInputStream(buf)
        ArchiveFormat.TAR_XZ, ArchiveFormat.XZ -> XZCompressorInputStream(buf, false, memoryLimitKb())
        else -> buf
    }

    private fun openStream(file: File, fmt: ArchiveFormat, ctl: ArchiveCtl): InputStream {
        val buf = BufferedInputStream(Counting(FileInputStream(file), ctl), 64 * 1024)
        try {
            return decompress(buf, fmt)
        } catch (e: Exception) {
            try {
                buf.close()
            } catch (ignored: Exception) {
            }
            throw e
        }
    }

    private fun openTar(file: File, fmt: ArchiveFormat, ctl: ArchiveCtl): TarArchiveInputStream {
        val s = openStream(file, fmt, ctl)
        try {
            return TarArchiveInputStream(s)
        } catch (e: Exception) {
            try {
                s.close()
            } catch (ignored: Exception) {
            }
            throw e
        }
    }

    private fun open7z(file: File, pw: String?): SevenZFile {
        val b = SevenZFile.builder().setFile(file)
        b.setMaxMemoryLimitKb(memoryLimitKb())
        if (!pw.isNullOrEmpty()) b.setPassword(pw.toCharArray())
        return b.get()
    }

    // ------------------------------------------------------------------ kompres

    /** Pengawas ruang kosong: periksa tiap [SPACE_CHECK_EVERY] byte masukan agar kompres tidak mengisi penuh penyimpanan. */
    private class SpaceGuard(private val dir: File) {
        private var since = 0L

        fun add(n: Int) {
            since += n
            if (since >= SPACE_CHECK_EVERY) {
                since = 0
                if (DiskSpace.lacks(dir, 0L)) throw ArchiveException(ArchiveFailure.NO_SPACE, "Ruang penyimpanan tidak cukup")
            }
        }
    }

    private class WalkStats {
        var skipped = 0
    }

    /**
     * Telusuri [files] berurutan (folder lebih dulu, anak diurutkan nama). Tautan simbolik, berkas khusus, dan
     * [skip] (arsip keluaran sendiri) dilewati. Folder yang tidak terbaca = galat, bukan arsip yang diam-diam kurang.
     */
    private fun walk(files: List<File>, skip: File, ctl: ArchiveCtl, stats: WalkStats, visit: (String, File, Boolean) -> Unit) {
        fun rec(f: File, path: String) {
            ctl.check()
            if (isLink(f)) {
                stats.skipped++
                return
            }
            if (f.canonicalFile == skip) return
            if (f.isDirectory) {
                visit(path, f, true)
                val kids = f.listFiles() ?: throw fail(ArchiveFailure.IO, "Folder tidak bisa dibaca")
                for (k in kids.sortedBy { it.name }) rec(k, path + "/" + k.name)
            } else if (f.isFile) {
                visit(path, f, false)
            } else {
                stats.skipped++
            }
        }
        for (f in files) rec(f, f.name)
    }

    private fun sizeOf(files: List<File>, ctl: ArchiveCtl): Long {
        fun rec(f: File): Long {
            ctl.check()
            if (isLink(f)) return 0L
            if (!f.isDirectory) return if (f.isFile) f.length() else 0L
            var sum = 0L
            f.listFiles()?.forEach { sum += rec(it) }
            return sum
        }
        return files.sumOf { rec(it) }
    }

    /** Baca tepat [size] byte dari [f]; berkas yang menyusut saat dibaca = galat, yang membesar dipotong di [size]. */
    private fun feed(f: File, size: Long, ctl: ArchiveCtl, guard: SpaceGuard, sink: (ByteArray, Int) -> Unit) {
        var left = size
        FileInputStream(f).use { ins ->
            val buf = ByteArray(IO_BUFFER)
            while (left > 0) {
                ctl.check()
                val n = ins.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) throw ArchiveException(ArchiveFailure.IO, "Berkas berubah saat dikompres")
                sink(buf, n)
                left -= n
                ctl.add(n.toLong())
                guard.add(n)
            }
        }
    }

    /**
     * Kompres [files] menjadi satu arsip di [destDir]; hasil tidak menimpa berkas lama (nama diberi " (1)").
     * Gagal atau dibatalkan = berkas keluaran dihapus. Tautan simbolik dan berkas khusus dilewati dan dihitung.
     * Kata sandi hanya untuk ZIP (AES-256); format lain ditolak, bukan ditulis tanpa sandi (fail-closed).
     */
    fun compress(files: List<File>, destDir: File, opt: CompressOptions, ctl: ArchiveCtl): CompressOutcome {
        val fmt = opt.format
        if (!fmt.canCompress) throw fail(ArchiveFailure.UNSUPPORTED, "Format ini hanya bisa diekstrak")
        val pw = opt.password?.takeIf { it.isNotEmpty() }
        if (files.isEmpty()) throw fail(ArchiveFailure.EMPTY_SOURCE, "Tidak ada yang dikompres")
        for (f in files) if (!NameRules.present(f)) throw fail(ArchiveFailure.NOT_FOUND, "Sumber tidak ditemukan")
        if (!destDir.isDirectory || !destDir.canWrite()) throw fail(ArchiveFailure.IO, "Folder tujuan tidak bisa ditulisi")
        if (files.groupBy { it.name }.values.any { it.size > 1 }) {
            throw fail(ArchiveFailure.NAME_CONFLICT, "Ada dua item bernama sama dari folder berbeda")
        }
        if (pw != null && !fmt.canEncrypt) throw fail(ArchiveFailure.PASSWORD_UNSUPPORTED, "Format ini belum mendukung kata sandi")
        val base = ArchiveNames.cleanBaseName(opt.baseName, fmt)
        ArchiveNames.baseNameProblem(base, fmt)?.let { throw fail(it, "Nama arsip tidak valid") }
        val level = fmt.clampLevel(opt.level)
        val out = ArchiveNames.uniqueOutput(destDir, base, fmt.ext)
        val skip = out.canonicalFile
        val guard = SpaceGuard(destDir)
        val stats = WalkStats()
        var entries = 0
        try {
            ctl.total = sizeOf(files, ctl)
            val stored = fmt == ArchiveFormat.TAR || level == 0
            if (DiskSpace.lacks(destDir, if (stored) ctl.total else 0L)) {
                throw fail(ArchiveFailure.NO_SPACE, "Ruang penyimpanan tidak cukup")
            }
            val n = when (fmt) {
                ArchiveFormat.ZIP -> writeZip(files, out, level, pw, ctl, guard, skip, stats)
                ArchiveFormat.SEVEN_Z -> write7z(files, out, level, ctl, guard, skip, stats)
                else -> writeTar(files, out, fmt, level, ctl, guard, skip, stats)
            }
            entries = n
            if (n == 0) throw fail(ArchiveFailure.EMPTY_SOURCE, "Tidak ada berkas yang bisa dikompres (tautan dan berkas khusus dilewati)")
            ctl.report()
        } catch (e: ArchiveCancelled) {
            out.delete()
            return CompressOutcome(null, 0, 0, true)
        } catch (e: OutOfMemoryError) {
            out.delete()
            throw fail(ArchiveFailure.OUT_OF_MEMORY, "Memori tidak cukup untuk level ini. Turunkan level kompresi.")
        } catch (e: Exception) {
            out.delete()
            throw mapWriteError(e)
        }
        return CompressOutcome(out, entries, stats.skipped, false)
    }

    private fun zipLevel(level: Int): CompressionLevel =
        CompressionLevel.values().firstOrNull { it.level == level } ?: CompressionLevel.NORMAL

    /** Tulis ZIP lewat aliran zip4j (bisa dibatalkan di tengah berkas); AES-256 bila [pw] ada. Mengembalikan jumlah entri. */
    private fun writeZip(
        files: List<File>,
        out: File,
        level: Int,
        pw: String?,
        ctl: ArchiveCtl,
        guard: SpaceGuard,
        skip: File,
        stats: WalkStats,
    ): Int {
        val raw = BufferedOutputStream(FileOutputStream(out), 64 * 1024)
        val zos = if (pw != null) ZipOutputStream(raw, pw.toCharArray()) else ZipOutputStream(raw)
        var count = 0
        try {
            walk(files, skip, ctl, stats) { path, f, dir ->
                val p = ZipParameters()
                p.setCompressionMethod(CompressionMethod.DEFLATE)
                p.setCompressionLevel(zipLevel(level))
                p.setFileNameInZip(if (dir) "$path/" else path)
                p.setLastModifiedFileTime(f.lastModified())
                if (pw != null && !dir) {
                    p.setEncryptFiles(true)
                    p.setEncryptionMethod(EncryptionMethod.AES)
                    p.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256)
                }
                zos.putNextEntry(p)
                if (!dir) feed(f, f.length(), ctl, guard) { b, n -> zos.write(b, 0, n) }
                zos.closeEntry()
                count++
            }
            zos.close()
        } finally {
            try {
                zos.close()
            } catch (ignored: Exception) {
            }
            try {
                raw.close()
            } catch (ignored: Exception) {
            }
        }
        return count
    }

    /** Tulis tar (opsional gz/bz2/xz); mengembalikan jumlah entri. Nama non-ASCII dan panjang lewat PAX. */
    private fun writeTar(
        files: List<File>,
        out: File,
        fmt: ArchiveFormat,
        level: Int,
        ctl: ArchiveCtl,
        guard: SpaceGuard,
        skip: File,
        stats: WalkStats,
    ): Int {
        val raw = BufferedOutputStream(FileOutputStream(out), 64 * 1024)
        var tos: TarArchiveOutputStream? = null
        var count = 0
        try {
            val comp: OutputStream = when (fmt) {
                ArchiveFormat.TAR_GZ -> GzipCompressorOutputStream(raw, GzipParameters().apply { compressionLevel = level })
                ArchiveFormat.TAR_BZ2 -> BZip2CompressorOutputStream(raw, level)
                ArchiveFormat.TAR_XZ -> XZCompressorOutputStream(raw, level)
                else -> raw
            }
            val t = TarArchiveOutputStream(comp)
            tos = t
            t.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            t.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
            t.setAddPaxHeadersForNonAsciiNames(true)
            walk(files, skip, ctl, stats) { path, f, dir ->
                val e = TarArchiveEntry(f, if (dir) "$path/" else path)
                t.putArchiveEntry(e)
                if (!dir) feed(f, e.size, ctl, guard) { b, n -> t.write(b, 0, n) }
                t.closeArchiveEntry()
                count++
            }
            t.finish()
            t.close()
            tos = null
        } finally {
            try {
                tos?.close()
            } catch (ignored: Exception) {
            }
            try {
                raw.close()
            } catch (ignored: Exception) {
            }
        }
        return count
    }

    /** Tulis 7z (LZMA2, atau COPY bila level 0); mengembalikan jumlah entri. Tanpa kata sandi: penulis 7z tidak mengenkripsi. */
    private fun write7z(
        files: List<File>,
        out: File,
        level: Int,
        ctl: ArchiveCtl,
        guard: SpaceGuard,
        skip: File,
        stats: WalkStats,
    ): Int {
        val sz = SevenZOutputFile(out)
        var count = 0
        try {
            walk(files, skip, ctl, stats) { path, f, dir ->
                val e = sz.createArchiveEntry(f, path)
                // Atribut Windows (0x10 folder, 0x20 arsip): 7-Zip tidak butuh, tetapi pembaca lain memakainya untuk mengenali folder
                e.setHasWindowsAttributes(true)
                e.setWindowsAttributes(if (dir) 0x10 else 0x20)
                if (dir) {
                    sz.putArchiveEntry(e)
                    sz.closeArchiveEntry()
                } else {
                    val size = f.length()
                    e.setContentMethods(methods7z(level, size))
                    sz.putArchiveEntry(e)
                    feed(f, size, ctl, guard) { b, n -> sz.write(b, 0, n) }
                    sz.closeArchiveEntry()
                }
                count++
            }
            sz.finish()
        } finally {
            try {
                sz.close()
            } catch (ignored: Exception) {
            }
        }
        return count
    }

    /**
     * Metode isi per berkas. commons-compress menulis tiap berkas sebagai blok sendiri dengan enkoder LZMA2 baru,
     * jadi kamus dikecilkan ke ukuran berkas (min 4 KB) agar ribuan berkas kecil tidak menelan puluhan MB per berkas.
     */
    private fun methods7z(level: Int, size: Long): List<SevenZMethodConfiguration> {
        if (level <= 0) return listOf(SevenZMethodConfiguration(SevenZMethod.COPY))
        val o = LZMA2Options()
        o.setPreset(level)
        var want = LZMA2Options.DICT_SIZE_MIN
        while (want < o.getDictSize() && want < size) want = want shl 1
        o.setDictSize(minOf(o.getDictSize(), want))
        return listOf(SevenZMethodConfiguration(SevenZMethod.LZMA2, o))
    }

    private fun mapWriteError(e: Exception): ArchiveException {
        if (e is ArchiveException) return e
        if (isNoSpace(e)) return fail(ArchiveFailure.NO_SPACE, "Ruang penyimpanan tidak cukup", e)
        if (e is java.io.FileNotFoundException) return fail(ArchiveFailure.IO, "Tidak bisa membaca atau menulis berkas", e)
        return fail(ArchiveFailure.IO, "Gagal mengompres", e)
    }
}
