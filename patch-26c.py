#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
patch-26c.py: Berkas: Mode Pilih (geser), Salin/Pindah/Hapus massal dengan progres dan batal.
Basis: commit 4c05805 (Patch 26a-26b). Idempotent: aman dijalankan berulang.
Jalankan dari root repo Molina_Studio:  python3 patch-26c.py
Bila ada berkas yang sudah Anda ubah sendiri, skrip berhenti SEBELUM menulis apa pun dan menyebut berkasnya.
"""
import hashlib
import os
import sys

BASE = "4c05805"

# (path, sha256 isi basis atau None bila berkas baru, isi baru)
FILES = [
    ('core-storage/src/main/java/com/molina/core/storage/TransferOperations.kt', None, r'''// molina-patch26c
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
'''),
    ('core-storage/src/main/java/com/molina/core/storage/TransferTypes.kt', None, r'''// molina-patch26c
package com.molina.core.storage

import com.molina.core.common.MolinaError

/**
 * Token pembatalan untuk operasi massal. Berbeda dengan pembatalan coroutine, hasil sebagian
 * (jumlah selesai/gagal) tetap dikembalikan, sehingga UI bisa melaporkan "dibatalkan, N selesai".
 */
class CancelToken {
    @Volatile
    var isCancelled: Boolean = false
        private set

    fun cancel() {
        isCancelled = true
    }
}

enum class TransferKind { COPY, MOVE, DELETE }

/**
 * Kemajuan operasi massal. [itemIndex] berbasis 0 (item tingkat atas yang sedang diproses);
 * [bytesTotal] adalah jumlah ukuran semua berkas hasil pemindaian awal.
 */
data class TransferProgress(
    val kind: TransferKind,
    val itemIndex: Int,
    val itemCount: Int,
    val bytesDone: Long,
    val bytesTotal: Long,
) {
    /** 0f..1f. Bila tidak ada byte (hanya folder kosong), dihitung dari jumlah item. */
    val fraction: Float
        get() = when {
            bytesTotal > 0 -> (bytesDone.toDouble() / bytesTotal).coerceIn(0.0, 1.0).toFloat()
            itemCount > 0 -> (itemIndex.toFloat() / itemCount).coerceIn(0f, 1f)
            else -> 0f
        }
}

/**
 * Ringkasan satu operasi massal. [skipped]: item yang tidak perlu dikerjakan (mis. dipindah ke folder
 * asalnya sendiri). [firstError]: galat item gagal pertama, tanpa nama/path.
 */
data class BatchResult(
    val kind: TransferKind,
    val succeeded: Int,
    val failed: Int,
    val skipped: Int,
    val cancelled: Boolean,
    val firstError: MolinaError?,
)
'''),
    ('core-storage/src/test/java/com/molina/core/storage/TransferOperationsTest.kt', None, r'''// molina-patch26c
package com.molina.core.storage

import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaResult
import com.molina.core.common.errorOrNull
import com.molina.core.common.getOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TransferOperationsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val never = { false }
    private val none = emptySet<File>()
    private val ignore: (TransferProgress) -> Unit = {}

    private fun dir(name: String, parent: File = tmp.root): File = File(parent, name).also { it.mkdirs() }

    private fun file(name: String, text: String, parent: File = tmp.root): File =
        File(parent, name).also { it.writeText(text) }

    private fun copy(src: List<File>, dest: File, cancel: () -> Boolean = never, p: (TransferProgress) -> Unit = ignore) =
        TransferOperations.copy(src, dest, cancel, p)

    @Test fun salinBerkas() {
        val src = file("a.txt", "isi")
        val dest = dir("tujuan")
        val r = copy(listOf(src), dest).getOrNull()!!
        assertEquals(1, r.succeeded)
        assertEquals(0, r.failed)
        assertFalse(r.cancelled)
        assertEquals("isi", File(dest, "a.txt").readText())
        assertTrue(src.exists())
    }

    @Test fun salinFolderRekursif() {
        val src = dir("sumber")
        file("x.txt", "x", src)
        file("y.txt", "y", dir("dalam", src))
        val dest = dir("tujuan")
        assertEquals(1, copy(listOf(src), dest).getOrNull()!!.succeeded)
        assertEquals("x", File(dest, "sumber/x.txt").readText())
        assertEquals("y", File(dest, "sumber/dalam/y.txt").readText())
        assertTrue(File(src, "x.txt").exists())
    }

    @Test fun salinKeFolderSamaMemberiNomor() {
        val src = file("a.txt", "1")
        copy(listOf(src), tmp.root)
        copy(listOf(src), tmp.root)
        assertEquals("1", File(tmp.root, "a (1).txt").readText())
        assertEquals("1", File(tmp.root, "a (2).txt").readText())
    }

    @Test fun salinNamaBentrokTidakMenimpa() {
        val src = file("a.txt", "baru")
        val dest = dir("tujuan")
        file("a.txt", "lama", dest)
        copy(listOf(src), dest)
        assertEquals("lama", File(dest, "a.txt").readText())
        assertEquals("baru", File(dest, "a (1).txt").readText())
    }

    @Test fun salinFolderKeDalamDirinyaDitolak() {
        val src = dir("sumber")
        val inner = dir("dalam", src)
        val r = copy(listOf(src), inner).getOrNull()!!
        assertEquals(0, r.succeeded)
        assertEquals(1, r.failed)
        assertEquals(ErrorKind.INVALID_PATH, r.firstError?.kind)
        assertEquals(0, inner.listFiles()!!.size)
    }

    @Test fun salinSumberHilangDihitungGagalTanpaMenghentikanYangLain() {
        val ada = file("ada.txt", "ok")
        val hilang = File(tmp.root, "hilang.txt")
        val dest = dir("tujuan")
        val r = copy(listOf(hilang, ada), dest).getOrNull()!!
        assertEquals(1, r.succeeded)
        assertEquals(1, r.failed)
        assertEquals(ErrorKind.NOT_FOUND, r.firstError?.kind)
        assertTrue(File(dest, "ada.txt").exists())
    }

    @Test fun salinTujuanTidakAdaGagal() {
        val src = file("a.txt", "1")
        val r = copy(listOf(src), File(tmp.root, "tidak-ada"))
        assertEquals(ErrorKind.NOT_FOUND, r.errorOrNull()?.kind)
    }

    @Test fun progresTerakhirSamaDenganTotal() {
        val a = file("a.bin", "x".repeat(1000))
        val b = file("b.bin", "y".repeat(2000))
        val dest = dir("tujuan")
        val seen = ArrayList<TransferProgress>()
        copy(listOf(a, b), dest, p = { seen += it })
        val last = seen.last()
        assertEquals(3000L, last.bytesTotal)
        assertEquals(3000L, last.bytesDone)
        assertEquals(1f, last.fraction, 0.0001f)
        assertEquals(2, last.itemCount)
        assertTrue(seen.zipWithNext().all { (x, y) -> y.bytesDone >= x.bytesDone })
    }

    @Test fun pecahanProgresDariJumlahItemBilaTanpaByte() {
        assertEquals(0.5f, TransferProgress(TransferKind.COPY, 1, 2, 0, 0).fraction, 0.0001f)
        assertEquals(0f, TransferProgress(TransferKind.COPY, 0, 0, 0, 0).fraction, 0.0001f)
        assertEquals(1f, TransferProgress(TransferKind.COPY, 0, 1, 20, 10).fraction, 0.0001f)
    }

    @Test fun dibatalkanSejakAwalTidakMenyalinApaApa() {
        val src = file("a.txt", "1")
        val dest = dir("tujuan")
        val r = copy(listOf(src), dest, cancel = { true }).getOrNull()!!
        assertTrue(r.cancelled)
        assertEquals(0, r.succeeded)
        assertEquals(0, dest.listFiles()!!.size)
    }

    @Test fun dibatalkanDiTengahMenghapusSalinanSetengahJadi() {
        val big = File(tmp.root, "besar.bin").also { it.writeBytes(ByteArray(3 * 1024 * 1024)) }
        val dest = dir("tujuan")
        val token = CancelToken()
        val r = TransferOperations.copy(listOf(big), dest, { token.isCancelled }) {
            if (it.bytesDone > 0) token.cancel()
        }.getOrNull()!!
        assertTrue(r.cancelled)
        assertEquals(0, r.succeeded)
        assertEquals(0, dest.listFiles()!!.size)
        assertTrue(big.exists())
    }

    @Test fun dibatalkanSaatItemKeduaMenyisakanItemPertama() {
        val a = file("a.txt", "1")
        val b = file("b.txt", "2")
        val dest = dir("tujuan")
        val token = CancelToken()
        val r = TransferOperations.copy(listOf(a, b), dest, { token.isCancelled }) {
            if (it.itemIndex == 1) token.cancel()
        }.getOrNull()!!
        assertTrue(r.cancelled)
        assertEquals(1, r.succeeded)
        assertTrue(File(dest, "a.txt").exists())
        assertFalse(File(dest, "b.txt").exists())
    }

    @Test fun pindahBerkas() {
        val src = file("a.txt", "isi")
        val dest = dir("tujuan")
        val r = TransferOperations.move(listOf(src), dest, none, never, ignore).getOrNull()!!
        assertEquals(1, r.succeeded)
        assertFalse(src.exists())
        assertEquals("isi", File(dest, "a.txt").readText())
    }

    @Test fun pindahFolderBerisi() {
        val src = dir("sumber")
        file("x.txt", "x", dir("dalam", src))
        val dest = dir("tujuan")
        TransferOperations.move(listOf(src), dest, none, never, ignore)
        assertFalse(src.exists())
        assertEquals("x", File(dest, "sumber/dalam/x.txt").readText())
    }

    @Test fun pindahKeFolderAsalDilewati() {
        val src = file("a.txt", "1")
        val r = TransferOperations.move(listOf(src), tmp.root, none, never, ignore).getOrNull()!!
        assertEquals(0, r.succeeded)
        assertEquals(1, r.skipped)
        assertTrue(src.exists())
    }

    @Test fun pindahDilindungiDitolak() {
        val logs = dir("logs")
        val dest = dir("tujuan")
        val r = TransferOperations.move(listOf(logs), dest, setOf(logs), never, ignore).getOrNull()!!
        assertEquals(1, r.failed)
        assertEquals(ErrorKind.PROTECTED, r.firstError?.kind)
        assertTrue(logs.exists())
    }

    @Test fun pindahInduknyaDilindungiDitolak() {
        val parent = dir("induk")
        val protectedChild = dir("logs", parent)
        val dest = dir("tujuan")
        val r = TransferOperations.move(listOf(parent), dest, setOf(protectedChild), never, ignore).getOrNull()!!
        assertEquals(ErrorKind.PROTECTED, r.firstError?.kind)
        assertTrue(protectedChild.exists())
    }

    @Test fun pindahFolderKeDalamDirinyaDitolak() {
        val src = dir("sumber")
        val inner = dir("dalam", src)
        val r = TransferOperations.move(listOf(src), inner, none, never, ignore).getOrNull()!!
        assertEquals(ErrorKind.INVALID_PATH, r.firstError?.kind)
        assertTrue(src.exists() && inner.exists())
    }

    @Test fun pindahNamaBentrokDiberiNomor() {
        val src = file("a.txt", "baru")
        val dest = dir("tujuan")
        file("a.txt", "lama", dest)
        TransferOperations.move(listOf(src), dest, none, never, ignore)
        assertEquals("lama", File(dest, "a.txt").readText())
        assertEquals("baru", File(dest, "a (1).txt").readText())
    }

    @Test fun hapusBanyakDenganYangDilindungi() {
        val a = file("a.txt", "1")
        val folder = dir("f")
        file("b.txt", "2", folder)
        val logs = dir("logs")
        val r = TransferOperations.deleteAll(listOf(a, folder, logs), setOf(logs), never, ignore).getOrNull()!!
        assertEquals(2, r.succeeded)
        assertEquals(1, r.failed)
        assertEquals(ErrorKind.PROTECTED, r.firstError?.kind)
        assertFalse(a.exists())
        assertFalse(folder.exists())
        assertTrue(logs.exists())
    }

    @Test fun hapusBerhasilTanpaGalatTidakMemilikiFirstError() {
        val a = file("a.txt", "1")
        val r = TransferOperations.deleteAll(listOf(a), none, never, ignore).getOrNull()!!
        assertNull(r.firstError)
        assertEquals(TransferKind.DELETE, r.kind)
    }

    @Test fun hapusDibatalkanSejakAwal() {
        val a = file("a.txt", "1")
        val r = TransferOperations.deleteAll(listOf(a), none, { true }, ignore).getOrNull()!!
        assertTrue(r.cancelled)
        assertTrue(a.exists())
    }

    @Test fun hapusProgresMencapaiTotal() {
        val a = file("a.bin", "x".repeat(500))
        val folder = dir("f")
        file("b.bin", "y".repeat(700), folder)
        val seen = ArrayList<TransferProgress>()
        TransferOperations.deleteAll(listOf(a, folder), none, never) { seen += it }
        assertEquals(1200L, seen.last().bytesTotal)
        assertEquals(1200L, seen.last().bytesDone)
    }

    @Test fun hasilKosongUntukDaftarKosong() {
        val r = copy(emptyList(), dir("tujuan")).getOrNull()!!
        assertEquals(0, r.succeeded + r.failed + r.skipped)
        assertTrue(MolinaResult.Success(r) is MolinaResult.Success)
    }
}
'''),
    ('files/src/main/java/com/molina/files/FilesSelection.kt', None, r'''// molina-patch26c
package com.molina.files

import com.molina.core.storage.TransferKind

/**
 * Keadaan Mode Pilih (murni, tanpa Android). Kunci item = path relatif terhadap root Molina.
 *
 * Aturan: geser kanan memilih (dan masuk Mode Pilih bila belum aktif), geser kiri membatalkan, dan
 * Mode Pilih keluar otomatis saat jumlah terpilih kembali 0. Satu-satunya keadaan aktif dengan 0
 * terpilih adalah saat mode dimasuki lewat menu "Pilih" dan belum ada yang dipilih.
 */
internal class FilesSelection {
    var active: Boolean = false
        private set

    private val keys = LinkedHashSet<String>()

    val count: Int get() = keys.size

    fun contains(key: String): Boolean = key in keys

    fun snapshot(): List<String> = keys.toList()

    /** Masuk Mode Pilih tanpa memilih apa pun (menu "Pilih"). */
    fun enter() {
        active = true
    }

    /** Geser kanan: masuk mode bila perlu lalu centang. true bila ada perubahan. */
    fun select(key: String): Boolean {
        active = true
        return keys.add(key)
    }

    /** Geser kiri: batal centang; keluar otomatis bila jadi kosong. true bila ada perubahan. */
    fun deselect(key: String): Boolean {
        if (!keys.remove(key)) return false
        if (keys.isEmpty()) active = false
        return true
    }

    /** Ketuk baris saat mode aktif. Mengembalikan true bila item kini terpilih. */
    fun toggle(key: String): Boolean {
        if (key in keys) deselect(key) else select(key)
        return key in keys
    }

    fun selectAll(all: Collection<String>) {
        active = true
        keys.addAll(all)
    }

    /** Keluar dari Mode Pilih dan mengosongkan pilihan. */
    fun exit() {
        keys.clear()
        active = false
    }

    /**
     * Membuang pilihan yang tidak lagi tampil (mis. setelah dihapus). Bila sebelumnya ada pilihan dan
     * kini kosong, mode keluar otomatis. Mode kosong dari menu tidak ikut keluar. true bila berubah.
     */
    fun retainOnly(visible: Set<String>): Boolean {
        val hadAny = keys.isNotEmpty()
        val changed = keys.retainAll(visible)
        if (hadAny && keys.isEmpty()) active = false
        return changed
    }

    fun restore(active: Boolean, saved: Collection<String>) {
        keys.clear()
        keys.addAll(saved)
        this.active = active || keys.isNotEmpty()
    }
}

/** Isi papan klip internal Berkas: item (path relatif) yang menunggu ditempel. [kind] COPY atau MOVE. */
internal data class FilesClip(val kind: TransferKind, val paths: List<String>)
'''),
    ('files/src/main/java/com/molina/files/FilesSwipe.kt', None, r'''// molina-patch26c
package com.molina.files

import android.graphics.Canvas
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * Geser baris untuk memilih. Baris belum terpilih hanya bisa digeser ke kanan (pilih), baris terpilih
 * hanya ke kiri (batal pilih), jadi arah selalu cocok dengan efeknya. Ambang 64 dp, baris ikut
 * bergeser paling jauh 28 dp (sama dengan penjelajah repo lama). Lemparan cepat tidak memicu.
 */
internal class FilesSwipeCallback(
    private val density: Float,
    private val isSelected: (position: Int) -> Boolean,
    private val onSelect: (position: Int) -> Unit,
    private val onDeselect: (position: Int) -> Unit,
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean = false

    override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val pos = viewHolder.bindingAdapterPosition
        if (pos == RecyclerView.NO_POSITION) return 0
        return if (isSelected(pos)) ItemTouchHelper.LEFT else ItemTouchHelper.RIGHT
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        val width = viewHolder.itemView.width.coerceAtLeast(1)
        return (THRESHOLD_DP * density / width).coerceIn(0.05f, 0.9f)
    }

    override fun getSwipeEscapeVelocity(defaultValue: Float): Float = defaultValue * VELOCITY_GUARD

    override fun getSwipeVelocityThreshold(defaultValue: Float): Float = defaultValue * VELOCITY_GUARD

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val pos = viewHolder.bindingAdapterPosition
        if (pos == RecyclerView.NO_POSITION) return
        if (direction == ItemTouchHelper.RIGHT) onSelect(pos) else onDeselect(pos)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        val cap = CAP_DP * density
        super.onChildDraw(c, recyclerView, viewHolder, (dX * DAMPING).coerceIn(-cap, cap), dY, actionState, isCurrentlyActive)
    }

    private companion object {
        const val THRESHOLD_DP = 64f
        const val CAP_DP = 28f
        const val DAMPING = 0.4f
        const val VELOCITY_GUARD = 50f
    }
}
'''),
    ('files/src/main/java/com/molina/files/FilesTransfer.kt', None, r'''// molina-patch26c
package com.molina.files

import com.molina.core.common.MolinaError
import com.molina.core.common.MolinaResult
import com.molina.core.storage.BatchResult
import com.molina.core.storage.CancelToken
import com.molina.core.storage.LocalMolinaStorage
import com.molina.core.storage.MolinaStorage
import com.molina.core.storage.TransferKind
import com.molina.core.storage.TransferProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Keadaan operasi massal yang dibaca Fragment. [Finished] sekali pakai: ditutup lewat [FilesTransfer.acknowledge]. */
internal sealed interface TransferState {
    object Idle : TransferState

    data class Running(
        val kind: TransferKind,
        val progress: TransferProgress?,
        val cancelling: Boolean,
    ) : TransferState

    data class Finished(
        val id: Long,
        val kind: TransferKind,
        val result: BatchResult?,
        val error: MolinaError?,
    ) : TransferState
}

/**
 * Menjalankan salin/pindah/hapus massal di luar siklus hidup Fragment, sehingga memutar layar atau
 * berpindah tab tidak menghentikan operasi. Tidak memegang Context/View. Satu operasi pada satu waktu.
 */
internal object FilesTransfer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val storage: MolinaStorage = LocalMolinaStorage()
    private val mutableState = MutableStateFlow<TransferState>(TransferState.Idle)
    private var token: CancelToken? = null
    private var seq = 0L

    val state: StateFlow<TransferState> get() = mutableState

    val isRunning: Boolean get() = mutableState.value is TransferState.Running

    /** Memulai operasi. false bila operasi lain masih berjalan atau [sources] kosong. Panggil dari main thread. */
    fun start(kind: TransferKind, sources: List<String>, destPath: String): Boolean {
        if (isRunning || sources.isEmpty()) return false
        val cancel = CancelToken()
        token = cancel
        mutableState.value = TransferState.Running(kind, null, false)
        FilesEvents.transferStart(kind, sources.size, destPath)
        val onProgress: (TransferProgress) -> Unit = { p ->
            mutableState.update { if (it is TransferState.Running) it.copy(progress = p) else it }
        }
        scope.launch {
            val result = when (kind) {
                TransferKind.COPY -> storage.copy(sources, destPath, cancel, onProgress)
                TransferKind.MOVE -> storage.move(sources, destPath, cancel, onProgress)
                TransferKind.DELETE -> storage.deleteAll(sources, cancel, onProgress)
            }
            finish(kind, result, destPath)
        }
        return true
    }

    /** Meminta pembatalan; hasil sebagian tetap dilaporkan lewat [TransferState.Finished]. */
    fun cancel() {
        val running = mutableState.value as? TransferState.Running ?: return
        token?.cancel()
        mutableState.value = running.copy(cancelling = true)
    }

    /** Menutup keadaan Finished yang sudah ditampilkan. */
    fun acknowledge(id: Long) {
        val s = mutableState.value
        if (s is TransferState.Finished && s.id == id) mutableState.value = TransferState.Idle
    }

    private fun finish(kind: TransferKind, result: MolinaResult<BatchResult>, destPath: String) {
        token = null
        val id = ++seq
        when (result) {
            is MolinaResult.Success -> {
                val r = result.value
                FilesEvents.transferDone(r, destPath)
                r.firstError?.let { FilesEvents.transferFailed(kind, it, destPath) }
                mutableState.value = TransferState.Finished(id, kind, r, null)
            }
            is MolinaResult.Failure -> {
                FilesEvents.transferFailed(kind, result.error, destPath)
                mutableState.value = TransferState.Finished(id, kind, null, result.error)
            }
        }
    }
}
'''),
    ('files/src/main/java/com/molina/files/TransferText.kt', None, r'''// molina-patch26c
package com.molina.files

import com.molina.core.storage.BatchResult
import com.molina.core.storage.TransferKind
import com.molina.core.storage.TransferProgress

/** Teks untuk panel progres dan ringkasan hasil operasi massal. Tanpa nama/path berkas. */
internal object TransferText {
    fun verb(kind: TransferKind): String = when (kind) {
        TransferKind.COPY -> "Menyalin"
        TransferKind.MOVE -> "Memindahkan"
        TransferKind.DELETE -> "Menghapus"
    }

    fun clipLabel(kind: TransferKind, count: Int): String = when (kind) {
        TransferKind.MOVE -> "Pindah $count item"
        else -> "Salin $count item"
    }

    /** Baris progres, mis. "Menyalin 2/5 \u00B7 45% \u00B7 12.0 MB". */
    fun progressLine(kind: TransferKind, p: TransferProgress?, cancelling: Boolean): String {
        if (cancelling) return "Membatalkan\u2026"
        if (p == null) return verb(kind) + "\u2026"
        val shown = (p.itemIndex + 1).coerceAtMost(p.itemCount.coerceAtLeast(1))
        val pct = (p.fraction * 100).toInt().coerceIn(0, 100)
        val size = if (p.bytesTotal > 0) " \u00B7 " + FileSizeFormat.format(p.bytesTotal) else ""
        return verb(kind) + " $shown/${p.itemCount} \u00B7 $pct%" + size
    }

    private fun done(kind: TransferKind): String = when (kind) {
        TransferKind.COPY -> "disalin"
        TransferKind.MOVE -> "dipindahkan"
        TransferKind.DELETE -> "dihapus"
    }

    /** Ringkasan akhir untuk Snackbar. */
    fun summary(r: BatchResult): String {
        val ok = "${r.succeeded} item ${done(r.kind)}"
        val skip = if (r.skipped > 0) ", ${r.skipped} dilewati" else ""
        return when {
            r.cancelled -> "Dibatalkan. $ok$skip" + if (r.failed > 0) ", ${r.failed} gagal" else ""
            r.failed > 0 -> {
                val why = r.firstError?.let { FilesErrorText.forTransfer(it.kind) }
                "$ok$skip, ${r.failed} gagal" + if (why != null) ": $why" else ""
            }
            r.succeeded == 0 && r.skipped > 0 -> "Tidak ada yang berubah (${r.skipped} item sudah di folder ini)"
            else -> ok + skip
        }
    }
}
'''),
    ('files/src/main/res/drawable/bg_files_row.xml', None, r'''<?xml version="1.0" encoding="utf-8"?>
<!-- molina-patch26c: riak sentuh + sorotan hijau saat baris terpilih (isActivated) -->
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="?android:attr/colorControlHighlight">
    <item>
        <selector>
            <item android:drawable="@color/files_row_selected" android:state_activated="true" />
            <item android:drawable="@android:color/transparent" />
        </selector>
    </item>
</ripple>
'''),
    ('files/src/main/res/drawable/ic_files_check_off.xml', None, r'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#99FFFFFF"
        android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z" />
</vector>
'''),
    ('files/src/main/res/drawable/ic_files_check_on.xml', None, r'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF2ECC71"
        android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8l-9,9z" />
</vector>
'''),
    ('files/src/main/res/values/colors_files.xml', None, r'''<?xml version="1.0" encoding="utf-8"?>
<!-- molina-patch26c: sorotan baris terpilih (hijau merek, transparan) -->
<resources>
    <color name="files_row_selected">#332ECC71</color>
    <color name="files_progress_track">#33FFFFFF</color>
</resources>
'''),
    ('files/src/test/java/com/molina/files/FilesSelectionTest.kt', None, r'''// molina-patch26c
package com.molina.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilesSelectionTest {
    private val s = FilesSelection()

    @Test fun geserKananMasukModeDanMemilih() {
        assertFalse(s.active)
        assertTrue(s.select("a"))
        assertTrue(s.active)
        assertEquals(1, s.count)
        s.select("b")
        assertEquals(listOf("a", "b"), s.snapshot())
    }

    @Test fun geserKiriMembatalkanDanKeluarOtomatisSaatNol() {
        s.select("a")
        s.select("b")
        assertTrue(s.deselect("a"))
        assertTrue(s.active)
        assertTrue(s.deselect("b"))
        assertFalse(s.active)
        assertEquals(0, s.count)
    }

    @Test fun geserKiriPadaYangTidakTerpilihTidakMengubahApaApa() {
        s.select("a")
        assertFalse(s.deselect("x"))
        assertTrue(s.active)
        assertEquals(1, s.count)
    }

    @Test fun ketukMembalikStatusDanKeluarSaatNol() {
        assertTrue(s.toggle("a"))
        assertTrue(s.toggle("b"))
        assertFalse(s.toggle("a"))
        assertTrue(s.active)
        assertFalse(s.toggle("b"))
        assertFalse(s.active)
    }

    @Test fun modeDariMenuTetapAktifDenganNolTerpilih() {
        s.enter()
        assertTrue(s.active)
        assertEquals(0, s.count)
    }

    @Test fun pilihSemua() {
        s.selectAll(listOf("a", "b", "c"))
        assertTrue(s.active)
        assertEquals(3, s.count)
        assertTrue(s.contains("b"))
    }

    @Test fun keluarMengosongkan() {
        s.select("a")
        s.exit()
        assertFalse(s.active)
        assertEquals(0, s.count)
    }

    @Test fun retainOnlyMembuangYangHilang() {
        s.selectAll(listOf("a", "b"))
        assertTrue(s.retainOnly(setOf("a", "z")))
        assertEquals(listOf("a"), s.snapshot())
        assertTrue(s.active)
    }

    @Test fun retainOnlyKeluarBilaSemuaHilang() {
        s.select("a")
        s.retainOnly(setOf("z"))
        assertFalse(s.active)
    }

    @Test fun retainOnlyTidakMengeluarkanModeKosongDariMenu() {
        s.enter()
        assertFalse(s.retainOnly(setOf("z")))
        assertTrue(s.active)
    }

    @Test fun restoreMengembalikanKeadaan() {
        s.restore(true, listOf("a", "b"))
        assertTrue(s.active)
        assertEquals(2, s.count)
        s.restore(false, emptyList())
        assertFalse(s.active)
        s.restore(false, listOf("a"))
        assertTrue(s.active)
    }
}
'''),
    ('files/src/test/java/com/molina/files/TransferTextTest.kt', None, r'''// molina-patch26c
package com.molina.files

import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaError
import com.molina.core.storage.BatchResult
import com.molina.core.storage.TransferKind
import com.molina.core.storage.TransferProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class TransferTextTest {
    private fun result(
        kind: TransferKind = TransferKind.COPY,
        ok: Int = 0,
        failed: Int = 0,
        skipped: Int = 0,
        cancelled: Boolean = false,
        error: MolinaError? = null,
    ) = BatchResult(kind, ok, failed, skipped, cancelled, error)

    @Test fun barisProgres() {
        val p = TransferProgress(TransferKind.COPY, 1, 5, 450, 1000)
        assertEquals("Menyalin 2/5 \u00B7 45% \u00B7 1000 B", TransferText.progressLine(TransferKind.COPY, p, false))
        assertEquals("Memindahkan\u2026", TransferText.progressLine(TransferKind.MOVE, null, false))
        assertEquals("Membatalkan\u2026", TransferText.progressLine(TransferKind.COPY, p, true))
    }

    @Test fun nomorItemTidakMelebihiJumlah() {
        val p = TransferProgress(TransferKind.DELETE, 3, 3, 0, 0)
        assertEquals("Menghapus 3/3 \u00B7 100%", TransferText.progressLine(TransferKind.DELETE, p, false))
    }

    @Test fun labelKlip() {
        assertEquals("Salin 3 item", TransferText.clipLabel(TransferKind.COPY, 3))
        assertEquals("Pindah 2 item", TransferText.clipLabel(TransferKind.MOVE, 2))
    }

    @Test fun ringkasanBerhasil() {
        assertEquals("3 item disalin", TransferText.summary(result(ok = 3)))
        assertEquals("2 item dipindahkan, 1 dilewati", TransferText.summary(result(TransferKind.MOVE, ok = 2, skipped = 1)))
        assertEquals("4 item dihapus", TransferText.summary(result(TransferKind.DELETE, ok = 4)))
    }

    @Test fun ringkasanSemuaDilewati() {
        assertEquals(
            "Tidak ada yang berubah (2 item sudah di folder ini)",
            TransferText.summary(result(TransferKind.MOVE, skipped = 2)),
        )
    }

    @Test fun ringkasanGagalMenyebutAlasan() {
        val e = MolinaError(ErrorKind.PROTECTED, "x")
        assertEquals(
            "1 item dihapus, 1 gagal: Folder standar Molina dilindungi",
            TransferText.summary(result(TransferKind.DELETE, ok = 1, failed = 1, error = e)),
        )
    }

    @Test fun ringkasanDibatalkan() {
        assertEquals("Dibatalkan. 1 item disalin", TransferText.summary(result(ok = 1, cancelled = true)))
        assertEquals("Dibatalkan. 0 item disalin, 1 gagal", TransferText.summary(result(failed = 1, cancelled = true)))
    }
}
'''),
    ('core-storage/src/main/java/com/molina/core/storage/MolinaStorage.kt', 'e976630466cb096f797729ef787c4ce767ee68daa200f7c7495b15f423878927', r'''package com.molina.core.storage

import com.molina.core.common.DefaultDispatcherProvider
import com.molina.core.common.DispatcherProvider
import com.molina.core.common.MolinaPaths
import com.molina.core.common.MolinaResult
import com.molina.core.common.molinaCatching
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** Akses berkas di bawah root Molina. UI hanya bergantung pada antarmuka ini. */
interface MolinaStorage {
    /** Membuat root dan subfolder standar bila belum ada. Aman dipanggil berulang. */
    suspend fun ensureLayout(): MolinaResult<Unit>

    /** Mendaftar isi folder [relative] (relatif terhadap root). Folder lebih dulu, lalu nama. */
    suspend fun list(relative: String = ""): MolinaResult<List<FileEntry>>

    // molina-patch26a
    /** Daftar isi [relative] dengan pencarian, sortir, dan filter sesuai [options]. */
    suspend fun browse(relative: String = "", options: ListOptions = ListOptions()): MolinaResult<ListResult>

    suspend fun createFolder(parentRelative: String, name: String): MolinaResult<FileEntry>

    suspend fun createFile(parentRelative: String, name: String): MolinaResult<FileEntry>

    /** Mengubah nama item di [relative] (hanya nama, bukan path). Root dan folder standar dilindungi. */
    suspend fun rename(relative: String, newName: String): MolinaResult<FileEntry>

    /** Menghapus item (rekursif). Root dan folder standar dilindungi. */
    suspend fun delete(relative: String): MolinaResult<Unit>

    // molina-patch26c
    /**
     * Menyalin [sources] ke folder [destRelative]. Nama bentrok diberi nomor, tidak menimpa. Folder tidak
     * bisa disalin ke dalam dirinya sendiri. Hasil sebagian tetap dikembalikan saat [cancel] dipicu.
     */
    suspend fun copy(
        sources: List<String>,
        destRelative: String,
        cancel: CancelToken = CancelToken(),
        onProgress: (TransferProgress) -> Unit = {},
    ): MolinaResult<BatchResult>

    /** Memindahkan [sources] ke [destRelative]. Root dan folder standar dilindungi. */
    suspend fun move(
        sources: List<String>,
        destRelative: String,
        cancel: CancelToken = CancelToken(),
        onProgress: (TransferProgress) -> Unit = {},
    ): MolinaResult<BatchResult>

    /** Menghapus banyak item sekaligus (rekursif). Root dan folder standar dilindungi. */
    suspend fun deleteAll(
        relatives: List<String>,
        cancel: CancelToken = CancelToken(),
        onProgress: (TransferProgress) -> Unit = {},
    ): MolinaResult<BatchResult>
}

class LocalMolinaStorage(
    private val paths: MolinaPaths = MolinaPaths(),
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) : MolinaStorage {

    /** Root dan folder standar: tidak boleh dihapus atau diganti namanya. */
    private val protectedPaths: Set<File> get() = paths.layout.toSet()

    override suspend fun ensureLayout(): MolinaResult<Unit> = withContext(dispatchers.io) {
        molinaCatching {
            for (dir in paths.layout) {
                if (dir.isDirectory) continue
                if (dir.exists()) throw IOException("bukan folder")
                if (!dir.mkdirs() && !dir.isDirectory) throw IOException("gagal membuat folder")
            }
        }
    }

    override suspend fun list(relative: String): MolinaResult<List<FileEntry>> =
        withContext(dispatchers.io) {
            when (val resolved = PathGuard.resolveInside(paths.root, relative)) {
                is MolinaResult.Failure -> resolved
                is MolinaResult.Success -> listDirectory(resolved.value)
            }
        }

    override suspend fun browse(relative: String, options: ListOptions): MolinaResult<ListResult> =
        withContext(dispatchers.io) {
            when (val resolved = PathGuard.resolveInside(paths.root, relative)) {
                is MolinaResult.Failure -> resolved
                is MolinaResult.Success -> molinaCatching {
                    val dir = resolved.value
                    if (!dir.exists()) throw FileNotFoundException()
                    if (!dir.isDirectory) throw IOException("bukan folder")
                    EntryLister.list(dir, options) { !isActive } ?: throw SecurityException()
                }
            }
        }

    override suspend fun createFolder(parentRelative: String, name: String): MolinaResult<FileEntry> =
        withContext(dispatchers.io) {
            onResolved(parentRelative) { FileOperations.createFolder(it, name) }
        }

    override suspend fun createFile(parentRelative: String, name: String): MolinaResult<FileEntry> =
        withContext(dispatchers.io) {
            onResolved(parentRelative) { FileOperations.createFile(it, name) }
        }

    override suspend fun rename(relative: String, newName: String): MolinaResult<FileEntry> =
        withContext(dispatchers.io) {
            onResolved(relative) { FileOperations.rename(it, newName, protectedPaths) }
        }

    override suspend fun delete(relative: String): MolinaResult<Unit> =
        withContext(dispatchers.io) {
            onResolved(relative) { FileOperations.delete(it, protectedPaths) { !isActive } }
        }

    // molina-patch26c
    override suspend fun copy(
        sources: List<String>,
        destRelative: String,
        cancel: CancelToken,
        onProgress: (TransferProgress) -> Unit,
    ): MolinaResult<BatchResult> = withContext(dispatchers.io) {
        onTransferInputs(sources, destRelative) { files, dest ->
            TransferOperations.copy(files, dest, { cancel.isCancelled || !isActive }, onProgress)
        }
    }

    override suspend fun move(
        sources: List<String>,
        destRelative: String,
        cancel: CancelToken,
        onProgress: (TransferProgress) -> Unit,
    ): MolinaResult<BatchResult> = withContext(dispatchers.io) {
        onTransferInputs(sources, destRelative) { files, dest ->
            TransferOperations.move(files, dest, protectedPaths, { cancel.isCancelled || !isActive }, onProgress)
        }
    }

    override suspend fun deleteAll(
        relatives: List<String>,
        cancel: CancelToken,
        onProgress: (TransferProgress) -> Unit,
    ): MolinaResult<BatchResult> = withContext(dispatchers.io) {
        when (val files = resolveAll(relatives)) {
            is MolinaResult.Failure -> files
            is MolinaResult.Success ->
                TransferOperations.deleteAll(files.value, protectedPaths, { cancel.isCancelled || !isActive }, onProgress)
        }
    }

    private inline fun onTransferInputs(
        sources: List<String>,
        destRelative: String,
        block: (List<File>, File) -> MolinaResult<BatchResult>,
    ): MolinaResult<BatchResult> {
        val files = when (val r = resolveAll(sources)) {
            is MolinaResult.Failure -> return r
            is MolinaResult.Success -> r.value
        }
        return when (val dest = PathGuard.resolveInside(paths.root, destRelative)) {
            is MolinaResult.Failure -> dest
            is MolinaResult.Success -> block(files, dest.value)
        }
    }

    /** Menyelesaikan semua path relatif; satu path tidak valid menggagalkan seluruh permintaan. */
    private fun resolveAll(relatives: List<String>): MolinaResult<List<File>> {
        val out = ArrayList<File>(relatives.size)
        for (rel in relatives) {
            when (val r = PathGuard.resolveInside(paths.root, rel)) {
                is MolinaResult.Failure -> return r
                is MolinaResult.Success -> out += r.value
            }
        }
        return MolinaResult.Success(out)
    }

    private inline fun <T> onResolved(relative: String, block: (File) -> MolinaResult<T>): MolinaResult<T> =
        when (val resolved = PathGuard.resolveInside(paths.root, relative)) {
            is MolinaResult.Failure -> resolved
            is MolinaResult.Success -> block(resolved.value)
        }

    private fun listDirectory(dir: File): MolinaResult<List<FileEntry>> = molinaCatching {
        if (!dir.exists()) throw FileNotFoundException()
        if (!dir.isDirectory) throw IOException("bukan folder")
        val children = dir.listFiles() ?: throw SecurityException()
        children
            .map { FileEntry(it.name, it.path, it.isDirectory, if (it.isDirectory) 0L else it.length(), it.lastModified()) }
            .sortedWith(compareByDescending<FileEntry> { it.isDirectory }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
}
'''),
    ('core-storage/src/test/java/com/molina/core/storage/LocalMolinaStorageTest.kt', '4a8416a244e4209a80e696903a4c8cbfce2c84a30e644faa7bf09f5ba8301488', r'''package com.molina.core.storage

import com.molina.core.common.DispatcherProvider
import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaPaths
import com.molina.core.common.errorOrNull
import com.molina.core.common.getOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private object TestDispatchers : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Unconfined
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
}

class LocalMolinaStorageTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun storage(root: File) = LocalMolinaStorage(MolinaPaths(root), TestDispatchers)

    @Test fun ensureLayoutMembuatFolderDanIdempotent() = runBlocking {
        val root = File(tmp.root, "Molina")
        val s = storage(root)
        assertTrue(s.ensureLayout().getOrNull() != null)
        assertTrue(File(root, "logs").isDirectory && File(root, "terminal").isDirectory && File(root, "cinema").isDirectory)
        assertTrue(s.ensureLayout().getOrNull() != null)
    }

    @Test fun ensureLayoutGagalBilaRootAdalahBerkas() = runBlocking {
        val root = tmp.newFile("Molina")
        assertEquals(ErrorKind.IO, storage(root).ensureLayout().errorOrNull()?.kind)
    }

    @Test fun listUrutFolderDuluLaluNamaTanpaPeduliHurufBesar() = runBlocking {
        val root = tmp.newFolder("Molina")
        File(root, "b.txt").writeText("12345")
        File(root, "A.txt").writeText("1")
        File(root, "zdir").mkdir()
        File(root, "Adir").mkdir()
        val names = storage(root).list().getOrNull()!!.map { it.name }
        assertEquals(listOf("Adir", "zdir", "A.txt", "b.txt"), names)
    }

    @Test fun listMelaporkanUkuranBerkasDanNolUntukFolder() = runBlocking {
        val root = tmp.newFolder("Molina")
        File(root, "b.txt").writeText("12345")
        File(root, "d").mkdir()
        val byName = storage(root).list().getOrNull()!!.associateBy { it.name }
        assertEquals(5L, byName["b.txt"]!!.sizeBytes)
        assertEquals(0L, byName["d"]!!.sizeBytes)
        assertTrue(byName["d"]!!.isDirectory)
    }

    @Test fun listSubfolderRelatif() = runBlocking {
        val root = tmp.newFolder("Molina")
        File(root, "sub").mkdir()
        File(root, "sub/x.txt").writeText("x")
        assertEquals(listOf("x.txt"), storage(root).list("sub").getOrNull()!!.map { it.name })
    }

    @Test fun listFolderTidakAdaMenghasilkanNotFound() = runBlocking {
        val root = tmp.newFolder("Molina")
        assertEquals(ErrorKind.NOT_FOUND, storage(root).list("tidak-ada").errorOrNull()?.kind)
    }

    @Test fun listBerkasBukanFolderMenghasilkanIo() = runBlocking {
        val root = tmp.newFolder("Molina")
        File(root, "f.txt").writeText("x")
        assertEquals(ErrorKind.IO, storage(root).list("f.txt").errorOrNull()?.kind)
    }

    @Test fun listKeluarRootDitolak() = runBlocking {
        val root = tmp.newFolder("Molina")
        assertEquals(ErrorKind.INVALID_PATH, storage(root).list("..").errorOrNull()?.kind)
    }

    // molina-patch26a
    @Test fun browseKueriDanSortirLewatStorage() = runBlocking {
        val root = tmp.newFolder("Molina")
        File(root, "Film A.mkv").writeText("1")
        File(root, "Film B.mkv").writeText("12")
        File(root, "catatan.txt").writeText("x")
        val r = storage(root).browse("", ListOptions(query = "film", sortKey = SortKey.SIZE, ascending = false)).getOrNull()!!
        assertEquals(listOf("Film B.mkv", "Film A.mkv"), r.entries.map { it.name })
    }

    @Test fun browseKeluarRootDitolak() = runBlocking {
        val root = tmp.newFolder("Molina")
        assertEquals(ErrorKind.INVALID_PATH, storage(root).browse("..").errorOrNull()?.kind)
    }

    @Test fun createFolderDanFileDiSubfolder() = runBlocking {
        val root = tmp.newFolder("Molina")
        File(root, "sub").mkdir()
        val s = storage(root)
        assertTrue(s.createFolder("sub", "baru").getOrNull()!!.isDirectory)
        assertTrue(!s.createFile("sub", "a.sh").getOrNull()!!.isDirectory)
        assertTrue(File(root, "sub/baru").isDirectory && File(root, "sub/a.sh").isFile)
        assertEquals(ErrorKind.ALREADY_EXISTS, s.createFolder("sub", "baru").errorOrNull()?.kind)
        assertEquals(ErrorKind.INVALID_PATH, s.createFolder("../luar", "x").errorOrNull()?.kind)
    }

    @Test fun renameDanDeleteBerjalan() = runBlocking {
        val root = tmp.newFolder("Molina")
        File(root, "lama.txt").writeText("x")
        val s = storage(root)
        assertEquals("baru.txt", s.rename("lama.txt", "baru.txt").getOrNull()!!.name)
        assertTrue(s.delete("baru.txt").getOrNull() != null)
        assertTrue(!File(root, "baru.txt").exists())
    }

    @Test fun folderStandarDanRootDilindungi() = runBlocking {
        val root = tmp.newFolder("Molina")
        val s = storage(root)
        s.ensureLayout()
        assertEquals(ErrorKind.PROTECTED, s.delete("logs").errorOrNull()?.kind)
        assertEquals(ErrorKind.PROTECTED, s.rename("terminal", "x").errorOrNull()?.kind)
        assertEquals(ErrorKind.PROTECTED, s.delete("").errorOrNull()?.kind)
        assertTrue(File(root, "logs").isDirectory && File(root, "terminal").isDirectory)
    }

    // molina-patch26c
    @Test fun salinPindahHapusMassalLewatRelatif() = runBlocking {
        val root = File(tmp.root, "Molina")
        val s = storage(root)
        s.ensureLayout()
        File(root, "a.txt").writeText("A")
        File(root, "b.txt").writeText("B")
        File(root, "tujuan").mkdir()
        val copied = s.copy(listOf("a.txt", "b.txt"), "tujuan").getOrNull()!!
        assertEquals(2, copied.succeeded)
        assertTrue(File(root, "a.txt").exists() && File(root, "tujuan/a.txt").exists())
        val moved = s.move(listOf("a.txt"), "tujuan").getOrNull()!!
        assertEquals(1, moved.succeeded)
        assertTrue(!File(root, "a.txt").exists() && File(root, "tujuan/a (1).txt").exists())
        val deleted = s.deleteAll(listOf("b.txt", "tujuan")).getOrNull()!!
        assertEquals(2, deleted.succeeded)
        assertTrue(!File(root, "b.txt").exists() && !File(root, "tujuan").exists())
    }

    @Test fun pindahDanHapusMassalMenolakFolderStandar() = runBlocking {
        val root = File(tmp.root, "Molina")
        val s = storage(root)
        s.ensureLayout()
        File(root, "tujuan").mkdir()
        assertEquals(ErrorKind.PROTECTED, s.move(listOf("logs"), "tujuan").getOrNull()!!.firstError?.kind)
        assertEquals(ErrorKind.PROTECTED, s.deleteAll(listOf("logs", "terminal")).getOrNull()!!.firstError?.kind)
        assertTrue(File(root, "logs").isDirectory && File(root, "terminal").isDirectory)
    }

    @Test fun massalMenolakPathKeluarDariRoot() = runBlocking {
        val root = File(tmp.root, "Molina")
        val s = storage(root)
        s.ensureLayout()
        assertEquals(ErrorKind.INVALID_PATH, s.copy(listOf("../luar"), "").errorOrNull()?.kind)
        assertEquals(ErrorKind.INVALID_PATH, s.deleteAll(listOf("../luar")).errorOrNull()?.kind)
        assertEquals(ErrorKind.INVALID_PATH, s.copy(listOf("a"), "../luar").errorOrNull()?.kind)
    }

    @Test fun salinFolderRootKeSubfolderDitolak() = runBlocking {
        val root = File(tmp.root, "Molina")
        val s = storage(root)
        s.ensureLayout()
        val r = s.copy(listOf(""), "logs").getOrNull()!!
        assertEquals(ErrorKind.INVALID_PATH, r.firstError?.kind)
    }
}
'''),
    ('files/src/main/java/com/molina/files/FilesAdapter.kt', 'a36dff62fedbbcc7c70dc0b7402ba4ce3a5242372ecb906614c72d6d4d1d266d', r'''package com.molina.files

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import android.content.res.ColorStateList
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.molina.core.storage.FileEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.molina.core.ui.R as UiR

private object FileEntryDiff : DiffUtil.ItemCallback<FileEntry>() {
    override fun areItemsTheSame(oldItem: FileEntry, newItem: FileEntry): Boolean = oldItem.path == newItem.path
    override fun areContentsTheSame(oldItem: FileEntry, newItem: FileEntry): Boolean = oldItem == newItem
}

/**
 * molina-patch26b: ikon per jenis, nama putih polos, titik tiga per baris.
 * molina-patch26c: Mode Pilih (kotak centang menggantikan titik tiga, baris terpilih disorot).
 * [keyOf] memberi kunci unik item (path relatif) untuk mencocokkan pilihan.
 */
internal class FilesAdapter(
    private val keyOf: (FileEntry) -> String,
    private val onClick: (FileEntry) -> Unit,
    private val onMenu: (FileEntry, View) -> Unit,
) : ListAdapter<FileEntry, FilesAdapter.Holder>(FileEntryDiff) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private var selectionActive = false
    private var selectedKeys: Set<String> = emptySet()

    fun isSelectedAt(position: Int): Boolean {
        val entry = currentList.getOrNull(position) ?: return false
        return selectionActive && keyOf(entry) in selectedKeys
    }

    /** Memperbarui tampilan pilihan tanpa mengubah daftar. */
    fun setSelection(active: Boolean, keys: Set<String>) {
        if (active == selectionActive && keys == selectedKeys) return
        selectionActive = active
        selectedKeys = keys
        notifyItemRangeChanged(0, itemCount)
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.file_icon)
        val name: TextView = view.findViewById(R.id.file_name)
        val info: TextView = view.findViewById(R.id.file_info)
        val more: ImageButton = view.findViewById(R.id.file_more)
        val check: ImageView = view.findViewById(R.id.file_check)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        val holder = Holder(view)
        view.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClick(getItem(pos))
        }
        holder.more.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onMenu(getItem(pos), holder.more)
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = getItem(position)
        val ctx = holder.itemView.context
        val date = dateFormat.format(Date(entry.lastModifiedMs))
        holder.name.text = if (entry.isDirectory) entry.name + "/" else entry.name
        val where = if (entry.relDir.isNullOrEmpty()) "" else entry.relDir + " \u00B7 "
        holder.info.text = where +
            if (entry.isDirectory) "Folder \u00B7 $date" else FileSizeFormat.format(entry.sizeBytes) + " \u00B7 $date"
        holder.icon.setImageResource(if (entry.isDirectory) R.drawable.ic_files_folder else R.drawable.ic_files_file)
        val tint = when {
            entry.isDirectory -> UiR.color.molina_tab_brand
            entry.extension.lowercase(Locale.ROOT) in VIDEO_EXT -> UiR.color.molina_cinema_accent
            else -> UiR.color.molina_on_surface_variant
        }
        ImageViewCompat.setImageTintList(holder.icon, ColorStateList.valueOf(ContextCompat.getColor(ctx, tint)))
        holder.itemView.alpha = if (entry.isHidden) 0.6f else 1f
        val picked = selectionActive && keyOf(entry) in selectedKeys
        holder.itemView.isActivated = picked
        holder.more.visibility = if (selectionActive) View.GONE else View.VISIBLE
        holder.check.visibility = if (selectionActive) View.VISIBLE else View.GONE
        holder.check.setImageResource(if (picked) R.drawable.ic_files_check_on else R.drawable.ic_files_check_off)
        holder.check.contentDescription = if (picked) "Terpilih" else "Tidak terpilih"
    }

    private companion object {
        val VIDEO_EXT = setOf("mp4", "mkv", "avi", "mov", "webm", "ts", "m4v", "flv", "wmv", "3gp", "mpg", "mpeg")
    }
}
'''),
    ('files/src/main/java/com/molina/files/FilesErrorText.kt', 'e0ccfff82924622e18f2fbf2fd2026dc551d639ee92e375b54976fa5dfe0a43e', r'''package com.molina.files

import com.molina.core.common.ErrorKind

/** Teks galat untuk pengguna. Sengaja tanpa path (lihat docs/penamaan.md). */
object FilesErrorText {
    fun of(kind: ErrorKind): String = when (kind) {
        ErrorKind.NOT_FOUND -> "Folder tidak ditemukan"
        ErrorKind.PERMISSION_DENIED ->
            "Izin penyimpanan belum diberikan. Jika dialog tidak muncul, aktifkan lewat Setelan Android > Aplikasi > Molina > Izin."
        ErrorKind.INVALID_PATH -> "Path tidak valid"
        ErrorKind.INVALID_NAME -> "Nama tidak valid (kosong, memuat \"/\", atau lebih dari 255 karakter)"
        ErrorKind.ALREADY_EXISTS -> "Nama sudah dipakai di folder ini"
        ErrorKind.PROTECTED -> "Folder ini dilindungi dan tidak bisa diubah atau dihapus"
        ErrorKind.IO -> "Kesalahan I/O saat membaca folder"
        ErrorKind.UNKNOWN -> "Kesalahan tak terduga"
    }

    /** Teks galat untuk operasi tulis (buat/ganti nama/hapus). */
    fun forOp(kind: ErrorKind): String = when (kind) {
        ErrorKind.NOT_FOUND -> "Item tidak ditemukan"
        ErrorKind.PERMISSION_DENIED -> "Akses ditolak. Periksa izin penyimpanan"
        ErrorKind.IO -> "Operasi gagal (periksa izin tulis)"
        else -> of(kind)
    }

    // molina-patch26c
    /** Teks galat untuk salin/pindah/hapus massal. */
    fun forTransfer(kind: ErrorKind): String = when (kind) {
        ErrorKind.INVALID_PATH -> "Tujuan berada di dalam folder yang disalin atau dipindah"
        ErrorKind.PROTECTED -> "Folder standar Molina dilindungi"
        else -> forOp(kind)
    }
}
'''),
    ('files/src/main/java/com/molina/files/FilesEvents.kt', 'cac1537f37df0ac5869c40e494fc2d44a8c1d24f992800b0852edabe9d842b32', r'''// molina-patch13c
package com.molina.files

import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaError
import com.molina.core.storage.BatchResult
import com.molina.core.storage.TransferKind
import com.molina.log.LogLevel
import com.molina.log.MolinaEvents
import com.molina.log.MolinaLog
import com.molina.log.MolinaLogger

/**
 * Peristiwa terstruktur area `files` (kode otomatis berawalan `files.`). Path TIDAK pernah dicatat:
 * hanya kedalaman folder, jumlah isi, jenis galat, kelas exception, dan kelas errno. Stack trace
 * sengaja tidak dilampirkan karena pesan exception sistem file biasanya memuat path penuh.
 */
internal object FilesEvents {
    private const val AREA = "files"
    private val ERRNO = Regex(
        """\b(EACCES|EPERM|ENOENT|ENOTDIR|EISDIR|EIO|ENOSPC|EROFS|EBUSY|ELOOP|ENAMETOOLONG|EMFILE|ENFILE|ESTALE|ENODEV|ENXIO)\b""",
    )

    /** Jumlah segmen path relatif; root = 0. */
    fun depth(path: String): Int = if (path.isEmpty()) 0 else path.count { it == '/' } + 1

    fun listed(path: String, count: Int, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "list",
            fields = mapOf("depth" to depth(path).toString(), "count" to count.toString()),
            logger = logger,
        )

    // molina-patch26b
    /** [name]: create-folder | create-file | rename | delete. Tanpa nama/path; hanya jenis dan kedalaman. */
    fun op(name: String, directory: Boolean, path: String, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "op-$name",
            fields = mapOf("kind" to if (directory) "folder" else "file", "depth" to depth(path).toString()),
            logger = logger,
        )

    /** Pencarian nama (kueri TIDAK dicatat, hanya mode dan jumlah hasil). */
    fun searched(path: String, count: Int, deep: Boolean, truncated: Boolean, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "search",
            fields = mapOf(
                "depth" to depth(path).toString(), "deep" to deep.toString(),
                "count" to count.toString(), "truncated" to truncated.toString(),
            ),
            logger = logger,
        )

    // molina-patch26c
    /** [action]: enter | add | remove | all | exit; [via]: swipe | tap | menu | back | op. Tanpa nama/path. */
    fun selection(action: String, via: String, count: Int, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "select",
            fields = mapOf("action" to action, "via" to via, "count" to count.toString()),
            logger = logger,
        )

    /** [action]: set | clear (papan klip internal Salin/Pindah). */
    fun clip(action: String, kind: TransferKind?, count: Int, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "clip",
            fields = linkedMapOf("action" to action, "mode" to (kind?.opName() ?: "none"), "count" to count.toString()),
            logger = logger,
        )

    fun transferStart(kind: TransferKind, count: Int, destPath: String, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "transfer-start",
            fields = mapOf("op" to kind.opName(), "count" to count.toString(), "depth" to depth(destPath).toString()),
            logger = logger,
        )

    fun transferDone(r: BatchResult, destPath: String, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(
            AREA, "transfer-done",
            fields = mapOf(
                "op" to r.kind.opName(), "ok" to r.succeeded.toString(), "failed" to r.failed.toString(),
                "skipped" to r.skipped.toString(), "cancelled" to r.cancelled.toString(),
                "depth" to depth(destPath).toString(),
            ),
            logger = logger,
        )

    /** Kode kegagalan memakai [opName]: files.copy-failed | files.move-failed | files.delete-batch-failed. */
    fun transferFailed(kind: TransferKind, error: MolinaError, path: String, logger: MolinaLogger = MolinaLog.logger) =
        failed(kind.opName(), error, path, logger)

    private fun TransferKind.opName(): String = when (this) {
        TransferKind.COPY -> "copy"
        TransferKind.MOVE -> "move"
        TransferKind.DELETE -> "delete-batch"
    }

    fun permissionMissing(path: String, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(AREA, "permission-missing", fields = mapOf("depth" to depth(path).toString()), logger = logger)

    fun permissionRequested(logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(AREA, "permission-request", logger = logger)

    fun permissionResult(granted: Boolean, logger: MolinaLogger = MolinaLog.logger) =
        MolinaEvents.behavior(AREA, "permission-result", fields = mapOf("granted" to granted.toString()), logger = logger)

    /** [op] = list | layout | create-folder | create-file | rename | delete -> kode files.<op>-failed (BUG). */
    fun failed(op: String, error: MolinaError, path: String, logger: MolinaLogger = MolinaLog.logger) {
        val cause = error.cause
        val fields = linkedMapOf(
            "kind" to error.kind.name,
            "error" to (cause?.javaClass?.simpleName?.ifEmpty { null } ?: "none"),
            "depth" to depth(path).toString(),
        )
        errnoOf(cause)?.let { fields["errno"] = it }
        val level = when (error.kind) {
            ErrorKind.NOT_FOUND, ErrorKind.INVALID_PATH, ErrorKind.INVALID_NAME,
            ErrorKind.ALREADY_EXISTS, ErrorKind.PROTECTED -> LogLevel.WARN
            else -> LogLevel.ERROR
        }
        MolinaEvents.bug(AREA, op + "-failed", error.message, fields = fields, level = level, logger = logger)
    }

    /** Hanya nama errno baku yang diambil dari rantai penyebab; sisa pesan (path) dibuang. */
    fun errnoOf(t: Throwable?): String? =
        generateSequence(t) { it.cause }.take(5).firstNotNullOfOrNull { ERRNO.find(it.message.orEmpty())?.value }
}
'''),
    ('files/src/main/java/com/molina/files/FilesFragment.kt', '542c123b91e53fbcf28c5ff986a9e109d01c993eabbad4208befe95eb3be78fc', r'''package com.molina.files

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaError
import com.molina.core.common.MolinaResult
import com.molina.core.storage.FileEntry
import com.molina.core.storage.ListOptions
import com.molina.core.storage.ListResult
import com.molina.core.storage.LocalMolinaStorage
import com.molina.core.storage.MolinaStorage
import com.molina.core.storage.SortKey
import com.molina.core.storage.TransferKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.molina.core.ui.R as UiR

/**
 * Penjelajah folder Molina. molina-patch26b: header bingkai hijau, cari nama (opsional sampai subfolder),
 * sortir, berkas tersembunyi, serta Folder baru / Berkas baru / Ganti nama / Hapus lewat titik tiga.
 * molina-patch26c: Mode Pilih (geser kanan memilih, geser kiri membatalkan, otomatis keluar saat 0
 * terpilih), Salin/Pindah/Hapus massal dengan progres dan batal, serta bilah Tempel.
 * Seluruh akses berkas lewat :core-storage.
 */
class FilesFragment : Fragment(R.layout.fragment_files) {
    private val storage: MolinaStorage = LocalMolinaStorage()
    private val adapter = FilesAdapter(
        keyOf = { entry -> keyOf(entry) },
        onClick = { entry -> onEntryClick(entry) },
        onMenu = { entry, anchor -> showEntryMenu(entry, anchor) },
    )
    private val selection = FilesSelection()
    private var clip: FilesClip? = null
    private var swipeHelper: ItemTouchHelper? = null

    private var views: Views? = null
    private var backCallback: OnBackPressedCallback? = null
    private var loadJob: Job? = null
    private var queryJob: Job? = null
    private var currentPath = ""
    private var options = ListOptions()

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            FilesEvents.permissionResult(granted)
            if (views != null) load(currentPath)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentPath = savedInstanceState?.getString(KEY_PATH).orEmpty()
        if (savedInstanceState != null) {
            options = restoreOptions(savedInstanceState)
            restoreSelectionAndClip(savedInstanceState)
        }
        val v = Views(view)
        views = v
        v.list.layoutManager = LinearLayoutManager(requireContext())
        v.list.adapter = adapter
        swipeHelper = ItemTouchHelper(
            FilesSwipeCallback(
                density = resources.displayMetrics.density,
                isSelected = { pos -> adapter.isSelectedAt(pos) },
                onSelect = { pos -> onSwipeSelect(pos) },
                onDeselect = { pos -> onSwipeDeselect(pos) },
            ),
        ).also { it.attachToRecyclerView(v.list) }
        v.clipPaste.setOnClickListener { pasteHere() }
        v.clipCancel.setOnClickListener { clearClip() }
        v.progressCancel.setOnClickListener { FilesTransfer.cancel() }
        v.up.setOnClickListener { goUp() }
        v.menu.setOnClickListener { showHeaderMenu(it) }
        v.retry.setOnClickListener { onRetry() }
        v.search.setText(options.query)
        v.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = onQueryEdited(s?.toString().orEmpty())
        })
        v.search.setOnEditorActionListener { tv, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                tv.clearFocus()
                true
            } else {
                false
            }
        }
        val back = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                goUp()
            }
        }
        backCallback = back
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, back)
        applyClip()
        applySelection()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                FilesTransfer.state.collect { renderTransfer(it) }
            }
        }
        load(currentPath)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PATH, currentPath)
        outState.putString(KEY_QUERY, options.query)
        outState.putString(KEY_SORT, options.sortKey.name)
        outState.putBoolean(KEY_ASC, options.ascending)
        outState.putBoolean(KEY_FOLDERS_FIRST, options.foldersFirst)
        outState.putBoolean(KEY_HIDDEN, options.showHidden)
        outState.putBoolean(KEY_DEEP, options.deep)
        outState.putBoolean(KEY_SEL_ACTIVE, selection.active)
        outState.putStringArrayList(KEY_SEL_KEYS, ArrayList(selection.snapshot()))
        clip?.let {
            outState.putString(KEY_CLIP_KIND, it.kind.name)
            outState.putStringArrayList(KEY_CLIP_PATHS, ArrayList(it.paths))
        }
    }

    override fun onDestroyView() {
        loadJob?.cancel()
        loadJob = null
        queryJob?.cancel()
        queryJob = null
        backCallback = null
        swipeHelper?.attachToRecyclerView(null)
        swipeHelper = null
        views?.let { it.list.adapter = null }
        views = null
        super.onDestroyView()
    }

    private fun restoreOptions(b: Bundle) = ListOptions(
        sortKey = runCatching { SortKey.valueOf(b.getString(KEY_SORT).orEmpty()) }.getOrDefault(SortKey.NAME),
        ascending = b.getBoolean(KEY_ASC, true),
        foldersFirst = b.getBoolean(KEY_FOLDERS_FIRST, true),
        showHidden = b.getBoolean(KEY_HIDDEN, false),
        query = b.getString(KEY_QUERY).orEmpty(),
        deep = b.getBoolean(KEY_DEEP, false),
    )

    private fun restoreSelectionAndClip(b: Bundle) {
        selection.restore(b.getBoolean(KEY_SEL_ACTIVE, false), b.getStringArrayList(KEY_SEL_KEYS).orEmpty())
        val kind = runCatching { TransferKind.valueOf(b.getString(KEY_CLIP_KIND).orEmpty()) }.getOrNull()
        val paths = b.getStringArrayList(KEY_CLIP_PATHS).orEmpty()
        clip = if (kind != null && kind != TransferKind.DELETE && paths.isNotEmpty()) FilesClip(kind, paths) else null
    }

    /** Kunci item = path relatif terhadap root Molina (juga untuk hasil pencarian sampai subfolder). */
    private fun keyOf(entry: FileEntry): String = BrowserPath.entryPath(currentPath, entry.relDir, entry.name)

    // --- navigasi

    private fun onEntryClick(entry: FileEntry) {
        if (selection.active) {
            val picked = selection.toggle(keyOf(entry))
            FilesEvents.selection(
                action = if (picked) "add" else if (selection.active) "remove" else "exit",
                via = "tap",
                count = selection.count,
            )
            applySelection()
            return
        }
        if (entry.isDirectory) navigate(BrowserPath.entryPath(currentPath, entry.relDir, entry.name))
    }

    /** Pindah folder; kueri pencarian dikosongkan agar daftar folder baru tidak terfilter. */
    private fun navigate(path: String) {
        clearQuery()
        load(path)
    }

    private fun goUp() {
        if (selection.active) {
            exitSelection("back")
        } else if (options.query.isNotEmpty()) {
            clearQuery()
            load(currentPath)
        } else if (!BrowserPath.isRoot(currentPath)) {
            load(BrowserPath.parent(currentPath))
        }
    }

    private fun clearQuery() {
        queryJob?.cancel()
        if (options.query.isEmpty()) return
        options = options.copy(query = "")
        views?.search?.setText("")
    }

    // --- pencarian

    private fun onQueryEdited(text: String) {
        if (text.trim() == options.query) return
        queryJob?.cancel()
        queryJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(QUERY_DEBOUNCE_MS)
            options = options.copy(query = text.trim())
            load(currentPath)
        }
    }

    // --- izin dan muat

    private fun onRetry() {
        if (hasStoragePermission()) {
            load(currentPath)
        } else {
            FilesEvents.permissionRequested()
            permissionRequest.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun hasStoragePermission(): Boolean =
        requireContext().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    private fun load(path: String) {
        loadJob?.cancel()
        if (path != currentPath && selection.active) exitSelection("nav")
        currentPath = path
        showHeader(path)
        if (!hasStoragePermission()) {
            FilesEvents.permissionMissing(path)
            showError(ErrorKind.PERMISSION_DENIED)
            return
        }
        val opts = options
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            if (BrowserPath.isRoot(path)) {
                val layout = storage.ensureLayout()
                if (layout is MolinaResult.Failure) FilesEvents.failed("layout", layout.error, path)
            }
            if (opts.query.isNotEmpty() && opts.deep) showStatus("Mencari\u2026")
            when (val result = storage.browse(path, opts)) {
                is MolinaResult.Success -> {
                    FilesEvents.listed(path, result.value.entries.size)
                    if (opts.query.isNotEmpty()) {
                        FilesEvents.searched(path, result.value.entries.size, opts.deep, result.value.truncated)
                    }
                    showContent(result.value, opts)
                }
                is MolinaResult.Failure -> {
                    FilesEvents.failed("list", result.error, path)
                    showError(result.error.kind)
                }
            }
        }
    }

    // --- menu

    private fun showHeaderMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        val m = popup.menu
        if (selection.active) {
            val canAct = selection.count > 0 && !FilesTransfer.isRunning
            m.add(Menu.NONE, MENU_COPY, 0, "Salin").isEnabled = canAct
            m.add(Menu.NONE, MENU_MOVE, 1, "Pindah").isEnabled = canAct
            m.add(Menu.NONE, MENU_DELETE_SELECTED, 2, "Hapus").isEnabled = canAct
            m.add(Menu.NONE, MENU_SELECT_ALL, 3, "Pilih semua")
            m.add(Menu.NONE, MENU_SELECT_DONE, 4, "Selesai memilih")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_COPY -> setClip(TransferKind.COPY)
                    MENU_MOVE -> setClip(TransferKind.MOVE)
                    MENU_DELETE_SELECTED -> confirmDeleteSelection()
                    MENU_SELECT_ALL -> selectAllVisible()
                    MENU_SELECT_DONE -> exitSelection("menu")
                }
                true
            }
            popup.show()
            return
        }
        m.add(Menu.NONE, MENU_REFRESH, 0, "Segarkan")
        m.add(Menu.NONE, MENU_NEW_FOLDER, 1, "Folder baru")
        m.add(Menu.NONE, MENU_NEW_FILE, 2, "Berkas baru")
        m.add(Menu.NONE, MENU_SELECT, 3, "Pilih")
        val sorts = listOf(
            SortKey.NAME to "Urut: Nama", SortKey.DATE to "Urut: Tanggal",
            SortKey.SIZE to "Urut: Ukuran", SortKey.TYPE to "Urut: Tipe",
        )
        sorts.forEachIndexed { i, (key, label) ->
            m.add(GROUP_SORT, MENU_SORT_BASE + key.ordinal, 10 + i, label)
        }
        m.setGroupCheckable(GROUP_SORT, true, true)
        m.findItem(MENU_SORT_BASE + options.sortKey.ordinal)?.isChecked = true
        m.add(GROUP_FLAGS, MENU_DESC, 20, "Menurun").apply { isCheckable = true; isChecked = !options.ascending }
        m.add(GROUP_FLAGS, MENU_FOLDERS_FIRST, 21, "Folder di atas").apply { isCheckable = true; isChecked = options.foldersFirst }
        m.add(GROUP_FLAGS, MENU_HIDDEN, 22, "Tampilkan tersembunyi").apply { isCheckable = true; isChecked = options.showHidden }
        m.add(GROUP_FLAGS, MENU_DEEP, 23, "Cari sampai subfolder").apply { isCheckable = true; isChecked = options.deep }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_REFRESH -> load(currentPath)
                MENU_NEW_FOLDER -> promptCreate(folder = true)
                MENU_NEW_FILE -> promptCreate(folder = false)
                MENU_SELECT -> enterSelectionFromMenu()
                MENU_DESC -> applyOptions(options.copy(ascending = !options.ascending))
                MENU_FOLDERS_FIRST -> applyOptions(options.copy(foldersFirst = !options.foldersFirst))
                MENU_HIDDEN -> applyOptions(options.copy(showHidden = !options.showHidden))
                MENU_DEEP -> applyOptions(options.copy(deep = !options.deep))
                else -> {
                    val key = SortKey.values().firstOrNull { MENU_SORT_BASE + it.ordinal == item.itemId }
                    if (key != null) applyOptions(options.copy(sortKey = key))
                }
            }
            true
        }
        popup.show()
    }

    private fun applyOptions(next: ListOptions) {
        options = next
        load(currentPath)
    }

    private fun showEntryMenu(entry: FileEntry, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(Menu.NONE, MENU_RENAME, 0, "Ganti nama")
        popup.menu.add(Menu.NONE, MENU_DELETE, 1, "Hapus")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_RENAME -> promptRename(entry)
                MENU_DELETE -> promptDelete(entry)
            }
            true
        }
        popup.show()
    }

    // --- operasi

    private fun promptCreate(folder: Boolean) {
        FilesDialog.input(
            requireContext(),
            title = if (folder) "Folder baru" else "Berkas baru",
            initial = "",
            positive = "Buat",
            selectBase = false,
        ) { name ->
            runOp(if (folder) "create-folder" else "create-file", folder, currentPath) {
                if (folder) storage.createFolder(currentPath, name) else storage.createFile(currentPath, name)
            }
        }
    }

    private fun promptRename(entry: FileEntry) {
        val path = BrowserPath.entryPath(currentPath, entry.relDir, entry.name)
        FilesDialog.input(
            requireContext(),
            title = "Ganti nama",
            initial = entry.name,
            positive = "Ganti",
            selectBase = !entry.isDirectory,
        ) { name ->
            runOp("rename", entry.isDirectory, path) { storage.rename(path, name) }
        }
    }

    private fun promptDelete(entry: FileEntry) {
        val path = BrowserPath.entryPath(currentPath, entry.relDir, entry.name)
        val message = if (entry.isDirectory) {
            "\"${entry.name}\" dan seluruh isinya akan dihapus permanen."
        } else {
            "\"${entry.name}\" akan dihapus permanen."
        }
        FilesDialog.confirm(requireContext(), "Hapus?", message, "Hapus") {
            runOp("delete", entry.isDirectory, path) { storage.delete(path) }
        }
    }

    /** Menjalankan operasi tulis, mencatat peristiwa tanpa path, lalu memuat ulang folder aktif. */
    private fun runOp(op: String, directory: Boolean, path: String, block: suspend () -> MolinaResult<*>) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = block()) {
                is MolinaResult.Success -> {
                    FilesEvents.op(op, directory, path)
                    load(currentPath)
                }
                is MolinaResult.Failure -> {
                    reportOpFailure(op, result.error, path)
                    load(currentPath)
                }
            }
        }
    }

    private fun reportOpFailure(op: String, error: MolinaError, path: String) {
        FilesEvents.failed(op, error, path)
        val root = view ?: return
        Snackbar.make(root, FilesErrorText.forOp(error.kind), Snackbar.LENGTH_LONG).show()
    }

    // --- Mode Pilih dan operasi massal (molina-patch26c)

    private fun enterSelectionFromMenu() {
        selection.enter()
        FilesEvents.selection("enter", "menu", selection.count)
        applySelection()
    }

    private fun exitSelection(via: String) {
        if (!selection.active && selection.count == 0) return
        selection.exit()
        FilesEvents.selection("exit", via, 0)
        applySelection()
    }

    private fun selectAllVisible() {
        selection.selectAll(adapter.currentList.map { keyOf(it) })
        FilesEvents.selection("all", "menu", selection.count)
        applySelection()
    }

    private fun onSwipeSelect(position: Int) {
        val entry = adapter.currentList.getOrNull(position) ?: return
        val wasActive = selection.active
        selection.select(keyOf(entry))
        FilesEvents.selection(if (wasActive) "add" else "enter", "swipe", selection.count)
        views?.list?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        applySelection()
        adapter.notifyItemChanged(position)
    }

    private fun onSwipeDeselect(position: Int) {
        val entry = adapter.currentList.getOrNull(position) ?: return
        selection.deselect(keyOf(entry))
        FilesEvents.selection(if (selection.active) "remove" else "exit", "swipe", selection.count)
        views?.list?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        applySelection()
        adapter.notifyItemChanged(position)
    }

    /** Salin/Pindah: item terpilih masuk papan klip, lalu pengguna menavigasi ke tujuan dan menekan Tempel. */
    private fun setClip(kind: TransferKind) {
        val paths = selection.snapshot()
        if (paths.isEmpty()) return
        clip = FilesClip(kind, paths)
        FilesEvents.clip("set", kind, paths.size)
        exitSelection("op")
        applyClip()
    }

    private fun clearClip() {
        clip?.let { FilesEvents.clip("clear", it.kind, it.paths.size) }
        clip = null
        applyClip()
    }

    private fun pasteHere() {
        val c = clip ?: return
        if (!FilesTransfer.start(c.kind, c.paths, currentPath)) notifyBusy()
    }

    private fun confirmDeleteSelection() {
        val paths = selection.snapshot()
        if (paths.isEmpty()) return
        FilesDialog.confirm(
            requireContext(),
            title = "Hapus ${paths.size} item?",
            message = "Item terpilih dan seluruh isinya akan dihapus permanen.",
            positive = "Hapus",
        ) {
            if (!FilesTransfer.start(TransferKind.DELETE, paths, currentPath)) notifyBusy()
        }
    }

    private fun notifyBusy() {
        val root = view ?: return
        Snackbar.make(root, "Operasi lain masih berjalan", Snackbar.LENGTH_SHORT).show()
    }

    private fun renderTransfer(state: TransferState) {
        val v = views ?: return
        when (state) {
            TransferState.Idle -> v.progress.visibility = View.GONE
            is TransferState.Running -> {
                v.progress.visibility = View.VISIBLE
                v.progressText.text = TransferText.progressLine(state.kind, state.progress, state.cancelling)
                v.progressBar.setProgressCompat(((state.progress?.fraction ?: 0f) * 100).toInt(), true)
                v.progressCancel.isEnabled = !state.cancelling
            }
            is TransferState.Finished -> {
                v.progress.visibility = View.GONE
                onTransferFinished(state)
                FilesTransfer.acknowledge(state.id)
            }
        }
        applyClip()
    }

    private fun onTransferFinished(state: TransferState.Finished) {
        val text = state.result?.let { TransferText.summary(it) }
            ?: state.error?.let { FilesErrorText.forTransfer(it.kind) }
            ?: "Operasi gagal"
        view?.let { Snackbar.make(it, text, Snackbar.LENGTH_LONG).show() }
        if (state.kind == TransferKind.DELETE) exitSelection("op") else if (state.result != null) clearClip()
        load(currentPath)
    }

    private fun applySelection() {
        adapter.setSelection(selection.active, selection.snapshot().toSet())
        showHeader(currentPath)
    }

    private fun applyClip() {
        val v = views ?: return
        val c = clip
        v.clipBar.visibility = if (c == null) View.GONE else View.VISIBLE
        if (c != null) v.clipText.text = TransferText.clipLabel(c.kind, c.paths.size)
        v.clipPaste.isEnabled = !FilesTransfer.isRunning
    }

    private fun updateBack() {
        backCallback?.isEnabled = selection.active || options.query.isNotEmpty() || !BrowserPath.isRoot(currentPath)
    }

    // --- tampilan

    private fun showHeader(path: String) {
        val v = views ?: return
        val root = BrowserPath.isRoot(path)
        val picking = selection.active
        v.title.text = when {
            !picking -> BrowserPath.title(path)
            selection.count == 0 -> "Mode Pilih"
            else -> "${selection.count} dipilih"
        }
        v.title.setTextColor(
            if (picking) {
                ContextCompat.getColor(requireContext(), UiR.color.molina_key_green)
            } else {
                ContextCompat.getColor(requireContext(), android.R.color.white)
            },
        )
        val canUp = picking || !root
        v.up.isEnabled = canUp
        v.up.alpha = if (canUp) 1f else 0.35f
        updateBack()
    }

    private fun showStatus(text: String?) {
        val v = views ?: return
        v.status.text = text.orEmpty()
        v.status.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun showContent(result: ListResult, opts: ListOptions) {
        val v = views ?: return
        adapter.submitList(result.entries)
        v.retry.visibility = View.GONE
        selection.retainOnly(result.entries.mapTo(HashSet()) { keyOf(it) })
        applySelection()
        val searching = opts.query.isNotEmpty()
        showStatus(
            when {
                !searching -> null
                result.truncated -> "${result.entries.size} hasil (dipotong, maks. ${ListOptions.MAX_RESULTS})"
                else -> "${result.entries.size} hasil"
            },
        )
        if (result.entries.isEmpty()) {
            v.message.text = if (searching) "Tidak ada hasil" else "Folder kosong"
            v.message.visibility = View.VISIBLE
        } else {
            v.message.visibility = View.GONE
        }
    }

    private fun showError(kind: ErrorKind) {
        val v = views ?: return
        adapter.submitList(emptyList<FileEntry>())
        showStatus(null)
        v.message.text = FilesErrorText.of(kind)
        v.message.visibility = View.VISIBLE
        v.retry.text = if (kind == ErrorKind.PERMISSION_DENIED) "Beri izin" else "Coba lagi"
        v.retry.visibility = View.VISIBLE
    }

    private class Views(root: View) {
        val title: TextView = root.findViewById(R.id.files_title)
        val up: ImageButton = root.findViewById(R.id.files_up)
        val menu: ImageButton = root.findViewById(R.id.files_menu)
        val search: EditText = root.findViewById(R.id.files_search)
        val status: TextView = root.findViewById(R.id.files_status)
        val message: TextView = root.findViewById(R.id.files_message)
        val retry: MaterialButton = root.findViewById(R.id.files_retry)
        val list: RecyclerView = root.findViewById(R.id.files_list)
        val clipBar: LinearLayout = root.findViewById(R.id.files_clip_bar)
        val clipText: TextView = root.findViewById(R.id.files_clip_text)
        val clipPaste: MaterialButton = root.findViewById(R.id.files_clip_paste)
        val clipCancel: MaterialButton = root.findViewById(R.id.files_clip_cancel)
        val progress: LinearLayout = root.findViewById(R.id.files_progress)
        val progressText: TextView = root.findViewById(R.id.files_progress_text)
        val progressBar: LinearProgressIndicator = root.findViewById(R.id.files_progress_bar)
        val progressCancel: MaterialButton = root.findViewById(R.id.files_progress_cancel)
    }

    private companion object {
        const val KEY_PATH = "path"
        const val KEY_QUERY = "query"
        const val KEY_SORT = "sort"
        const val KEY_ASC = "asc"
        const val KEY_FOLDERS_FIRST = "folders_first"
        const val KEY_HIDDEN = "hidden"
        const val KEY_DEEP = "deep"
        const val KEY_SEL_ACTIVE = "sel_active"
        const val KEY_SEL_KEYS = "sel_keys"
        const val KEY_CLIP_KIND = "clip_kind"
        const val KEY_CLIP_PATHS = "clip_paths"
        const val QUERY_DEBOUNCE_MS = 250L

        const val GROUP_SORT = 1
        const val GROUP_FLAGS = 2
        const val MENU_REFRESH = 1
        const val MENU_NEW_FOLDER = 2
        const val MENU_NEW_FILE = 3
        const val MENU_RENAME = 4
        const val MENU_DELETE = 5
        const val MENU_DESC = 6
        const val MENU_FOLDERS_FIRST = 7
        const val MENU_HIDDEN = 8
        const val MENU_DEEP = 9
        const val MENU_SELECT = 10
        const val MENU_COPY = 11
        const val MENU_MOVE = 12
        const val MENU_DELETE_SELECTED = 13
        const val MENU_SELECT_ALL = 14
        const val MENU_SELECT_DONE = 15
        const val MENU_SORT_BASE = 100
    }
}
'''),
    ('files/src/main/res/layout/fragment_files.xml', '029a718a6326e91b465f300f9f4a710ebc51b071f1ee8f71697c83c58753fb07', r'''<?xml version="1.0" encoding="utf-8"?>
<!-- molina-patch26b; molina-patch26c: bilah Tempel dan panel progres -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/molina_bg"
    android:orientation="vertical">

    <LinearLayout
        android:id="@+id/files_header"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginStart="8dp"
        android:layout_marginTop="24dp"
        android:layout_marginEnd="8dp"
        android:background="@drawable/bg_files_header"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:padding="2dp">

        <ImageButton
            android:id="@+id/files_up"
            android:layout_width="44dp"
            android:layout_height="44dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="Naik satu folder"
            android:src="@drawable/ic_files_up"
            app:tint="@color/molina_key_green" />

        <TextView
            android:id="@+id/files_title"
            android:layout_width="0dp"
            android:layout_height="36dp"
            android:layout_marginStart="2dp"
            android:layout_marginEnd="4dp"
            android:layout_weight="1"
            android:background="@drawable/bg_files_box"
            android:ellipsize="start"
            android:gravity="center_vertical"
            android:maxLines="1"
            android:paddingStart="12dp"
            android:paddingEnd="12dp"
            android:textColor="@android:color/white"
            android:textSize="14sp" />

        <ImageButton
            android:id="@+id/files_menu"
            android:layout_width="44dp"
            android:layout_height="44dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="Menu berkas"
            android:src="@drawable/ic_files_more"
            app:tint="@android:color/white" />
    </LinearLayout>

    <EditText
        android:id="@+id/files_search"
        android:layout_width="match_parent"
        android:layout_height="40dp"
        android:layout_marginStart="8dp"
        android:layout_marginTop="8dp"
        android:layout_marginEnd="8dp"
        android:autofillHints="none"
        android:background="@drawable/bg_files_box"
        android:hint="Cari nama…"
        android:imeOptions="actionSearch"
        android:importantForAutofill="no"
        android:inputType="text"
        android:maxLines="1"
        android:paddingStart="12dp"
        android:paddingEnd="12dp"
        android:textColor="@android:color/white"
        android:textColorHint="@color/molina_tab_muted"
        android:textSize="14sp" />

    <TextView
        android:id="@+id/files_status"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:paddingStart="16dp"
        android:paddingTop="4dp"
        android:paddingEnd="16dp"
        android:textColor="@color/molina_tab_muted"
        android:textSize="12sp"
        android:visibility="gone" />

    <TextView
        android:id="@+id/files_message"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="16dp"
        android:textColor="@android:color/white"
        android:visibility="gone" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/files_retry"
        style="@style/Widget.Material3.Button.OutlinedButton"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="16dp"
        android:text="Coba lagi"
        android:visibility="gone" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/files_list"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:clipToPadding="false"
        android:paddingBottom="8dp" />

    <LinearLayout
        android:id="@+id/files_clip_bar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginStart="8dp"
        android:layout_marginTop="4dp"
        android:layout_marginEnd="8dp"
        android:background="@drawable/bg_files_box"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="12dp"
        android:paddingEnd="4dp"
        android:visibility="gone">

        <TextView
            android:id="@+id/files_clip_text"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:ellipsize="end"
            android:maxLines="1"
            android:textColor="@android:color/white"
            android:textSize="14sp" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/files_clip_paste"
            style="@style/Widget.Material3.Button.TextButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Tempel"
            android:textColor="@color/molina_key_green" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/files_clip_cancel"
            style="@style/Widget.Material3.Button.TextButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Batal"
            android:textColor="@color/molina_on_surface_variant" />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/files_progress"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginStart="8dp"
        android:layout_marginTop="4dp"
        android:layout_marginEnd="8dp"
        android:background="@drawable/bg_files_box"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:padding="12dp"
        android:visibility="gone">

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:id="@+id/files_progress_text"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:ellipsize="end"
                android:maxLines="1"
                android:textColor="@android:color/white"
                android:textSize="13sp" />

            <com.google.android.material.progressindicator.LinearProgressIndicator
                android:id="@+id/files_progress_bar"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:max="100"
                app:indicatorColor="@color/molina_key_green"
                app:trackColor="@color/files_progress_track" />
        </LinearLayout>

        <com.google.android.material.button.MaterialButton
            android:id="@+id/files_progress_cancel"
            style="@style/Widget.Material3.Button.TextButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="8dp"
            android:text="Batal"
            android:textColor="@color/molina_cinema_accent" />
    </LinearLayout>
</LinearLayout>
'''),
    ('files/src/main/res/layout/item_file.xml', 'b967c30e52d46183b7d42951059c9bde18fc7e1da99d9c174fcb07acf0e61536', r'''<?xml version="1.0" encoding="utf-8"?>
<!-- molina-patch26b: ikon + nama putih polos + titik tiga per baris (tanpa tekan lama); molina-patch26c: kotak centang Mode Pilih -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/bg_files_row"
    android:clickable="true"
    android:focusable="true"
    android:gravity="center_vertical"
    android:minHeight="56dp"
    android:orientation="horizontal"
    android:paddingStart="16dp"
    android:paddingTop="6dp"
    android:paddingBottom="6dp">

    <ImageView
        android:id="@+id/file_icon"
        android:layout_width="24dp"
        android:layout_height="24dp"
        android:importantForAccessibility="no" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="16dp"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/file_name"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:ellipsize="middle"
            android:maxLines="1"
            android:textAppearance="?attr/textAppearanceBodyLarge"
            android:textColor="@android:color/white" />

        <TextView
            android:id="@+id/file_info"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:ellipsize="start"
            android:maxLines="1"
            android:textAppearance="?attr/textAppearanceBodySmall"
            android:textColor="@color/molina_tab_muted" />
    </LinearLayout>

    <FrameLayout
        android:layout_width="44dp"
        android:layout_height="44dp">

        <ImageButton
            android:id="@+id/file_more"
            android:layout_width="44dp"
            android:layout_height="44dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="Opsi berkas"
            android:src="@drawable/ic_files_more"
            app:tint="@android:color/white" />

        <ImageView
            android:id="@+id/file_check"
            android:layout_width="44dp"
            android:layout_height="44dp"
            android:padding="10dp"
            android:visibility="gone" />
    </FrameLayout>
</LinearLayout>
'''),
    ('files/src/test/java/com/molina/files/FilesEventsTest.kt', 'f5d3c5f950b7b46fd7489659ece08b7d9fa81a8b364cab1398b5d51b1d3f4419', r'''// molina-patch13c
package com.molina.files

import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaError
import com.molina.core.storage.BatchResult
import com.molina.core.storage.TransferKind
import com.molina.log.LogEntry
import com.molina.log.LogLevel
import com.molina.log.LogSink
import com.molina.log.MolinaLogger
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FilesEventsTest {
    private class CaptureSink : LogSink {
        val entries = CopyOnWriteArrayList<LogEntry>()
        override fun write(entry: LogEntry) { entries += entry }
    }

    private val sink = CaptureSink()
    private val logger = MolinaLogger(listOf(sink))

    @Test
    fun depthCountsPathSegments() {
        assertEquals(0, FilesEvents.depth(""))
        assertEquals(1, FilesEvents.depth("Movies"))
        assertEquals(2, FilesEvents.depth("Movies/2024"))
    }

    @Test
    fun listedHasDepthAndCountOnly() {
        FilesEvents.listed("Movies/2024", 5, logger)
        val e = sink.entries.single()
        assertEquals("Molina/files", e.tag)
        assertEquals(LogLevel.INFO, e.level)
        assertEquals("[BEHAV:files.list] | depth=2 count=5", e.message)
    }

    @Test
    fun permissionEventsAreBehaviorNotBug() {
        FilesEvents.permissionMissing("", logger)
        FilesEvents.permissionRequested(logger)
        FilesEvents.permissionResult(false, logger)
        assertEquals(
            listOf(
                "[BEHAV:files.permission-missing] | depth=0",
                "[BEHAV:files.permission-request]",
                "[BEHAV:files.permission-result] | granted=false",
            ),
            sink.entries.map { it.message },
        )
    }

    @Test
    fun permissionDeniedIsBugWithErrnoAndNoPath() {
        val cause = IOException("/storage/emulated/0/Molina/rahasia: open failed: EACCES (Permission denied)")
        FilesEvents.failed("list", MolinaError(ErrorKind.PERMISSION_DENIED, "Akses ditolak", cause), "Movies", logger)
        val e = sink.entries.single()
        assertEquals(LogLevel.ERROR, e.level)
        assertEquals(
            "[BUG:files.list-failed] Akses ditolak | kind=PERMISSION_DENIED error=IOException depth=1 errno=EACCES",
            e.message,
        )
        assertFalse("rahasia" in e.message)
        assertNull(e.throwable)
    }

    @Test
    fun notFoundIsWarnLevel() {
        FilesEvents.failed("list", MolinaError(ErrorKind.NOT_FOUND, "Tidak ditemukan", FileNotFoundException()), "", logger)
        val e = sink.entries.single()
        assertEquals(LogLevel.WARN, e.level)
        assertEquals("[BUG:files.list-failed] Tidak ditemukan | kind=NOT_FOUND error=FileNotFoundException depth=0", e.message)
    }

    @Test
    fun layoutFailureHasOwnCodeAndNoCauseIsNone() {
        FilesEvents.failed("layout", MolinaError(ErrorKind.IO, "Kesalahan I/O"), "", logger)
        assertEquals(
            "[BUG:files.layout-failed] Kesalahan I/O | kind=IO error=none depth=0",
            sink.entries.single().message,
        )
    }

    @Test
    fun errnoIsFoundInCauseChainOnly() {
        val chain = RuntimeException("luar", IOException("ENOSPC (No space left on device)"))
        assertEquals("ENOSPC", FilesEvents.errnoOf(chain))
        assertNull(FilesEvents.errnoOf(IOException("folder bernama EMPTY")))
        assertNull(FilesEvents.errnoOf(null))
    }

    // molina-patch26b
    @Test
    fun opHanyaJenisDanKedalamanTanpaNama() {
        FilesEvents.op("rename", false, "Movies/2024/rahasia.mkv", logger)
        FilesEvents.op("create-folder", true, "", logger)
        assertEquals(
            listOf(
                "[BEHAV:files.op-rename] | kind=file depth=3",
                "[BEHAV:files.op-create-folder] | kind=folder depth=0",
            ),
            sink.entries.map { it.message },
        )
        assertFalse("rahasia" in sink.entries.first().message)
    }

    @Test
    fun pencarianTidakMencatatKueri() {
        FilesEvents.searched("Movies", 7, deep = true, truncated = false, logger = logger)
        assertEquals(
            "[BEHAV:files.search] | depth=1 deep=true count=7 truncated=false",
            sink.entries.single().message,
        )
    }

    @Test
    fun galatNamaSudahAdaDanDilindungiBerlevelWarn() {
        FilesEvents.failed("create-folder", MolinaError(ErrorKind.ALREADY_EXISTS, "Nama sudah dipakai"), "Movies", logger)
        FilesEvents.failed("delete", MolinaError(ErrorKind.PROTECTED, "Folder ini dilindungi"), "", logger)
        assertEquals(listOf(LogLevel.WARN, LogLevel.WARN), sink.entries.map { it.level })
        assertEquals(
            "[BUG:files.create-folder-failed] Nama sudah dipakai | kind=ALREADY_EXISTS error=none depth=1",
            sink.entries.first().message,
        )
    }

    // molina-patch26c
    @Test
    fun selectionAndClipEventsHaveNoPaths() {
        FilesEvents.selection("enter", "swipe", 1, logger)
        FilesEvents.clip("set", TransferKind.MOVE, 3, logger)
        FilesEvents.clip("clear", null, 0, logger)
        assertEquals(
            listOf(
                "[BEHAV:files.select] | action=enter via=swipe count=1",
                "[BEHAV:files.clip] | action=set mode=move count=3",
                "[BEHAV:files.clip] | action=clear mode=none count=0",
            ),
            sink.entries.map { it.message },
        )
    }

    @Test
    fun transferEventsCarryCountsOnly() {
        FilesEvents.transferStart(TransferKind.COPY, 4, "Movies/2024", logger)
        FilesEvents.transferDone(BatchResult(TransferKind.DELETE, 2, 1, 0, false, null), "", logger)
        assertEquals(
            listOf(
                "[BEHAV:files.transfer-start] | op=copy count=4 depth=2",
                "[BEHAV:files.transfer-done] | op=delete-batch ok=2 failed=1 skipped=0 cancelled=false depth=0",
            ),
            sink.entries.map { it.message },
        )
    }

    @Test
    fun transferFailureUsesOperationCode() {
        FilesEvents.transferFailed(
            TransferKind.MOVE,
            MolinaError(ErrorKind.PROTECTED, "Folder ini dilindungi", IOException("EACCES")),
            "Movies",
            logger,
        )
        val e = sink.entries.single()
        assertEquals(LogLevel.WARN, e.level)
        assertEquals(
            "[BUG:files.move-failed] Folder ini dilindungi | kind=PROTECTED error=IOException depth=1 errno=EACCES",
            e.message,
        )
    }
}
'''),
]

LOG_ANCHOR = "| `files.layout-failed` | BUG | folder standar Molina gagal dibuat | `kind`, `error`, `depth`, `errno` |"
LOG_ROWS = """
| `files.select` | BEHAV | Mode Pilih berubah (tanpa nama/path) | `action` (enter/add/remove/all/exit), `via` (swipe/tap/menu/back/nav/op), `count` |
| `files.clip` | BEHAV | papan klip Salin/Pindah diisi atau dikosongkan | `action` (set/clear), `mode` (copy/move/none), `count` |
| `files.transfer-start` | BEHAV | salin/pindah/hapus massal dimulai | `op` (copy/move/delete-batch), `count`, `depth` |
| `files.transfer-done` | BEHAV | operasi massal selesai (termasuk dibatalkan atau sebagian gagal) | `op`, `ok`, `failed`, `skipped`, `cancelled`, `depth` |
| `files.copy-failed` / `files.move-failed` / `files.delete-batch-failed` | BUG (W untuk galat nama/dilindungi/tujuan tidak valid) | tujuan tidak valid atau minimal satu item gagal (galat item pertama yang dicatat) | `kind`, `error`, `depth`, `errno` |"""

PROGRES_MARK = "Patch 26c: Berkas"
PROGRES = """- [x] Patch 26a-26b diverifikasi CI (kompilasi :files, jvm-tests)
- [x] Patch 26c: Berkas Mode Pilih + operasi massal. Data di :core-storage: TransferOperations (salin/pindah/hapus rekursif, nama bentrok diberi nomor tanpa menimpa, folder tidak bisa ke dalam dirinya, pindah memakai rename lalu salin+hapus bila gagal, folder standar dilindungi, salinan setengah jadi dibersihkan), CancelToken (hasil sebagian tetap dilaporkan), TransferProgress, BatchResult, MolinaStorage.copy/move/deleteAll. UI di :files: geser kanan memilih, geser kiri membatalkan, Mode Pilih keluar otomatis saat 0 terpilih (ambang 64 dp, geser maks. 28 dp seperti repo lama), menu titik tiga: Pilih/Salin/Pindah/Hapus/Pilih semua/Selesai memilih, bilah Tempel (papan klip internal, ikut putar layar), panel progres + Batal, FilesTransfer (operasi tetap jalan saat layar diputar), event files.select/clip/transfer-*, docs/log-contract.md diperbarui. 73 tes core-storage + 44 tes files lulus di sandbox; kode UI Android belum dikompilasi (tanpa Android SDK)
- [ ] Verifikasi CI Patch 26c (kompilasi :files: FilesFragment, FilesAdapter, FilesSwipe, layout; jvm-tests core-storage dan files)
- [ ] Uji perangkat Android 8: geser kanan memilih baris (getar singkat, baris menghijau, judul jadi N dipilih); geser kiri membatalkan; batal sampai 0 keluar mode otomatis; menu Pilih lalu ketuk baris; Pilih semua; Salin lalu masuk folder lain lalu Tempel; Pindah; salin ke folder yang sama menghasilkan nama (1); folder tidak bisa disalin/dipindah ke dalam dirinya; Hapus banyak; logs/terminal/cinema ditolak saat Pindah/Hapus; berkas besar: progres jalan, Batal menghapus salinan setengah jadi; putar layar saat operasi berjalan; Back keluar dari Mode Pilih lebih dulu
- [ ] Berikutnya: Patch 26d (header dan dialog Berkas digabung ke :core-ui, menyentuh :terminal), Patch 26e (Putar ke Cinema + thumbnail), lalu Favorit/Terakhir dibuka"""


def sha(b):
    return hashlib.sha256(b).hexdigest()


def read(path):
    with open(path, "rb") as f:
        return f.read()


def main():
    if not os.path.isdir(".git") or not os.path.exists("settings.gradle.kts"):
        sys.exit("Jalankan dari root repo Molina_Studio.")
    problems = []
    todo = []
    for path, base, new_text in FILES:
        new = new_text.encode("utf-8")
        if not os.path.exists(path):
            if base is not None:
                problems.append(path + " (berkas basis tidak ada)")
            else:
                todo.append((path, new))
            continue
        cur = read(path)
        if cur == new:
            continue
        if base is not None and sha(cur) == base:
            todo.append((path, new))
        else:
            problems.append(path + " (sudah berbeda dari basis " + BASE + ")")
    log_path = "docs/log-contract.md"
    log_text = None
    if os.path.exists(log_path):
        cur = read(log_path).decode("utf-8")
        if "files.transfer-start" not in cur:
            if LOG_ANCHOR in cur:
                log_text = cur.replace(LOG_ANCHOR, LOG_ANCHOR + LOG_ROWS, 1)
            else:
                problems.append(log_path + " (baris jangkar files.layout-failed tidak ditemukan)")
    else:
        problems.append(log_path + " (tidak ada)")
    if problems:
        print("BERHENTI: tidak ada berkas yang ditulis. Bermasalah:")
        for p in problems:
            print("  - " + p)
        sys.exit(1)
    for path, data in todo:
        d = os.path.dirname(path)
        if d:
            os.makedirs(d, exist_ok=True)
        with open(path, "wb") as f:
            f.write(data)
    if log_text is not None:
        with open(log_path, "wb") as f:
            f.write(log_text.encode("utf-8"))
    prog = "progres.md"
    prog_state = "sudah ada"
    cur = read(prog).decode("utf-8") if os.path.exists(prog) else ""
    if PROGRES_MARK not in cur:
        with open(prog, "w", encoding="utf-8") as f:
            f.write(cur + ("" if cur.endswith("\n") or not cur else "\n") + PROGRES + "\n")
        prog_state = "ditambahkan"
    print("Selesai: %d berkas ditulis, log-contract %s, progres.md %s." % (
        len(todo), "diperbarui" if log_text is not None else "sudah ada", prog_state))


if __name__ == "__main__":
    main()
