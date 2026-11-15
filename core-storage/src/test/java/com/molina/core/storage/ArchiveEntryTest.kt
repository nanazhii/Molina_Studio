// molina-patch26k5b
package com.molina.core.storage

import com.molina.core.common.DispatcherProvider
import com.molina.core.common.MolinaPaths
import com.molina.core.common.getOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private object EntryTestDispatchers : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Unconfined
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
}

/** Buka satu entri arsip lewat cache: tiap format, pagar keamanan, batas ukuran, LRU, batal, bersandi, storage. */
class ArchiveEntryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun ctl(cancel: () -> Boolean = { false }) = ArchiveCtl(ArchiveKind.EXTRACT, cancel, {})

    private fun dir(name: String): File = File(tmp.root, name).also { it.mkdirs() }

    private fun payload(seed: Long): ByteArray = ByteArray(64 * 1024).also { Random(seed).nextBytes(it) }

    /** Folder "proyek" berisi [names]; isi berkas ke-i = payload(i + 1). */
    private fun sample(names: List<String>): File {
        val src = dir("proyek")
        for ((i, n) in names.withIndex()) {
            val f = File(src, n)
            f.parentFile!!.mkdirs()
            f.writeBytes(payload(i + 1L))
        }
        return src
    }

    private fun build(fmt: ArchiveFormat, names: List<String> = listOf("a/b/data.bin"), pw: String? = null): File =
        ArchiveEngine.compress(
            listOf(sample(names)), dir("out-${fmt.name}-${names.size}"), CompressOptions(fmt, fmt.defaultLevel, "paket", pw),
            ArchiveCtl(ArchiveKind.COMPRESS, { false }, {}),
        ).file!!

    private fun nameOf(archive: File, suffix: String): String =
        ArchiveEngine.list(archive, null) { false }.entries.first { !it.isDirectory && it.name.endsWith(suffix) }.name

    private fun cache(
        name: String,
        maxEntry: Long = ArchiveEngineLimits.ENTRY_MAX_BYTES,
        maxTotal: Long = ArchiveEngineLimits.CACHE_MAX_BYTES,
        clock: () -> Long = { System.currentTimeMillis() },
    ) = ArchiveCache(File(tmp.root, name), maxEntry, maxTotal, clock)

    private fun assertReason(reason: ArchiveFailure, block: () -> Unit) {
        try {
            block()
            fail("seharusnya gagal: $reason")
        } catch (e: ArchiveException) {
            assertEquals(reason, e.reason)
        }
    }

    private fun leftovers(c: ArchiveCache): List<File> = c.root.listFiles().orEmpty().toList()

    @Test fun bukaEntriBersarangDiTiapFormat() {
        for (fmt in listOf(ArchiveFormat.ZIP, ArchiveFormat.SEVEN_Z, ArchiveFormat.TAR, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_BZ2, ArchiveFormat.TAR_XZ)) {
            val a = build(fmt)
            val c = cache("c-${fmt.name}")
            val r = c.open(a, nameOf(a, "data.bin"), null, ctl())!!
            assertEquals(fmt.name, "data.bin", r.file.name)
            assertEquals(fmt.name, 64L * 1024, r.bytes)
            assertFalse(fmt.name, r.cached)
            assertArrayEquals(fmt.name, payload(1L), r.file.readBytes())
            assertEquals(fmt.name, c.root.canonicalPath, r.file.parentFile!!.parentFile!!.canonicalPath)
        }
    }

    @Test fun gzTunggalDibuka() {
        val gz = File(tmp.root, "data.bin.gz")
        GzipCompressorOutputStream(FileOutputStream(gz)).use { it.write(payload(1L)) }
        val c = cache("c-gz")
        val r = c.open(gz, "data.bin", null, ctl())!!
        assertEquals("data.bin", r.file.name)
        assertArrayEquals(payload(1L), r.file.readBytes())
    }

    @Test fun pemakaianUlangDariCacheDanArsipBerubahMembuatSlotBaru() {
        val a = build(ArchiveFormat.ZIP)
        val n = nameOf(a, "data.bin")
        val c = cache("c-hit")
        val first = c.open(a, n, null, ctl())!!
        val second = c.open(a, n, null, ctl())!!
        assertFalse(first.cached)
        assertTrue(second.cached)
        assertEquals(first.file.canonicalPath, second.file.canonicalPath)
        a.setLastModified(a.lastModified() + 5000L)
        val third = c.open(a, n, null, ctl())!!
        assertFalse(third.cached)
        assertTrue(third.file.canonicalPath != first.file.canonicalPath)
    }

    @Test fun namaTidakAmanDitolakTanpaJejak() {
        val a = build(ArchiveFormat.ZIP)
        val c = cache("c-bad")
        assertReason(ArchiveFailure.UNSAFE_ENTRY) { c.open(a, "../x.txt", null, ctl()) }
        assertReason(ArchiveFailure.UNSAFE_ENTRY) { c.open(a, "a/../../x.txt", null, ctl()) }
        assertReason(ArchiveFailure.NO_MATCH) { c.open(a, "proyek/a/", null, ctl()) }
        assertReason(ArchiveFailure.NO_MATCH) { c.open(a, "tidak-ada.txt", null, ctl()) }
        assertReason(ArchiveFailure.NO_MATCH) { c.open(a, "", null, ctl()) }
        assertTrue(leftovers(c).isEmpty())
    }

    @Test fun zipSlipDiArsipDitolakDanTidakMenulisKeLuar() {
        val z = File(tmp.root, "jahat.zip")
        ZipOutputStream(FileOutputStream(z)).use { zo ->
            zo.putNextEntry(ZipEntry("ok.txt")); zo.write("x".toByteArray()); zo.closeEntry()
            zo.putNextEntry(ZipEntry("../jahat.txt")); zo.write("x".toByteArray()); zo.closeEntry()
        }
        val c = cache("c-slip")
        assertReason(ArchiveFailure.UNSAFE_ENTRY) { c.open(z, "ok.txt", null, ctl()) }
        assertFalse(File(tmp.root, "jahat.txt").exists())
        assertFalse(File(c.root, "jahat.txt").exists())
        assertTrue(leftovers(c).isEmpty())
    }

    @Test fun entriMelebihiBatasDitolakTanpaMemecahCache() {
        for (fmt in listOf(ArchiveFormat.ZIP, ArchiveFormat.SEVEN_Z, ArchiveFormat.TAR_GZ)) {
            val a = build(fmt)
            val c = cache("c-big-${fmt.name}", maxEntry = 1000L)
            assertReason(ArchiveFailure.TOO_LARGE) { c.open(a, nameOf(a, "data.bin"), null, ctl()) }
            assertTrue(fmt.name, leftovers(c).isEmpty())
        }
    }

    @Test fun entriPasPadaBatasDiterima() {
        val a = build(ArchiveFormat.TAR_GZ)
        val c = cache("c-edge", maxEntry = 64L * 1024)
        assertNotNull(c.open(a, nameOf(a, "data.bin"), null, ctl()))
    }

    @Test fun pangkasLruDanSentuhSaatDipakaiUlang() {
        var t = 1_000_000L
        val c = cache("c-lru", maxTotal = 150_000L, clock = { t += 10_000L; t })
        val z = build(ArchiveFormat.ZIP, listOf("a.bin", "b.bin", "c.bin"))
        val na = nameOf(z, "a.bin")
        val nb = nameOf(z, "b.bin")
        val nc = nameOf(z, "c.bin")
        val a = c.open(z, na, null, ctl())!!
        val b = c.open(z, nb, null, ctl())!!
        assertTrue(a.file.exists() && b.file.exists())
        val cc = c.open(z, nc, null, ctl())!!
        assertFalse("yang tertua (a) dipangkas", a.file.exists())
        assertTrue(b.file.exists() && cc.file.exists())
        val bHit = c.open(z, nb, null, ctl())!!
        assertTrue(bHit.cached)
        val a2 = c.open(z, na, null, ctl())!!
        assertFalse(a2.cached)
        assertFalse("c kini yang tertua", cc.file.exists())
        assertTrue(bHit.file.exists() && a2.file.exists())
        assertTotalWithin(c, 150_000L)
    }

    private fun assertTotalWithin(c: ArchiveCache, max: Long) {
        val total = c.root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        assertTrue("total $total > $max", total <= max)
    }

    @Test fun dibatalkanMengembalikanNullTanpaJejak() {
        for (fmt in listOf(ArchiveFormat.ZIP, ArchiveFormat.TAR_GZ)) {
            val a = build(fmt)
            val c = cache("c-cancel-${fmt.name}")
            assertNull(fmt.name, c.open(a, nameOf(a, "data.bin"), null, ctl(cancel = { true })))
            assertTrue(fmt.name, leftovers(c).isEmpty())
        }
    }

    @Test fun zipBersandiMeminta_sandi_lalu_berhasil() {
        val a = build(ArchiveFormat.ZIP, pw = "rahasia")
        val n = nameOf(a, "data.bin")
        val c = cache("c-pw")
        assertReason(ArchiveFailure.NEED_PASSWORD) { c.open(a, n, null, ctl()) }
        assertReason(ArchiveFailure.WRONG_PASSWORD) { c.open(a, n, "salah", ctl()) }
        assertTrue(leftovers(c).isEmpty())
        val ok = c.open(a, n, "rahasia", ctl())!!
        assertArrayEquals(payload(1L), ok.file.readBytes())
    }

    @Test fun clearMengosongkanTermasukSisaSementara() {
        val a = build(ArchiveFormat.ZIP)
        val c = cache("c-clear")
        c.open(a, nameOf(a, "data.bin"), null, ctl())
        File(c.root, ".tmp-sisa").mkdirs()
        File(c.root, ".tmp-sisa/x.bin").writeBytes(ByteArray(10))
        val freed = c.clear()
        assertTrue(freed >= 64L * 1024)
        assertTrue(leftovers(c).isEmpty())
        assertEquals(0L, cache("c-belum-ada").clear())
    }

    @Test fun lewatStorageDisimpanDiBawahRootDanDisembunyikan() = runBlocking {
        val root = tmp.newFolder("Molina")
        val a = build(ArchiveFormat.ZIP)
        a.copyTo(File(root, "paket.zip"))
        val s = LocalMolinaStorage(MolinaPaths(root), EntryTestDispatchers)
        val r = s.openArchiveEntry("paket.zip", nameOf(a, "data.bin")).getOrNull()!!
        assertFalse(r.cancelled)
        assertEquals("data.bin", r.name)
        assertTrue(r.relative.startsWith(MolinaPaths.ARSIP_CACHE_DIR + "/"))
        assertArrayEquals(payload(1L), File(root, r.relative).readBytes())
        assertFalse(s.list().getOrNull()!!.any { it.name == MolinaPaths.ARSIP_CACHE_DIR })
        val again = s.openArchiveEntry("paket.zip", nameOf(a, "data.bin")).getOrNull()!!
        assertTrue(again.cached)
        val freed = s.clearArchiveCache().getOrNull()!!
        assertTrue(freed >= 64L * 1024)
        assertTrue(File(root, MolinaPaths.ARSIP_CACHE_DIR).listFiles().orEmpty().isEmpty())
    }
}
