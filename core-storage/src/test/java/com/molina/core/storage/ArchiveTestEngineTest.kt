// molina-patch26k4
package com.molina.core.storage

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Random

/** Uji arsip (ArchiveEngine.test): arsip utuh, rusak (byte diubah / terpotong), bersandi, batal, tanpa tulis disk. */
class ArchiveTestEngineTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun ctl(cancel: () -> Boolean = { false }) = ArchiveCtl(ArchiveKind.EXTRACT, cancel, {})

    private fun dir(name: String): File = File(tmp.root, name).also { it.mkdirs() }

    /** Isi acak berseed 64 KB: hampir tidak terkompresi, sehingga byte tengah arsip pasti ada di data entri. */
    private fun payload(): ByteArray = ByteArray(64 * 1024).also { Random(42).nextBytes(it) }

    private fun sample(): File {
        val src = dir("src")
        File(src, "data.bin").writeBytes(payload())
        return src
    }

    private fun build(fmt: ArchiveFormat, pw: String? = null): File =
        ArchiveEngine.compress(
            listOf(sample()), dir("out-${fmt.name}"), CompressOptions(fmt, fmt.defaultLevel, "paket", pw),
            ArchiveCtl(ArchiveKind.COMPRESS, { false }, {}),
        ).file!!

    private fun single(name: String, wrap: (OutputStream) -> OutputStream): File {
        val f = File(tmp.root, name)
        wrap(FileOutputStream(f)).use { it.write(payload()) }
        return f
    }

    private fun flipMiddle(f: File) {
        val b = f.readBytes()
        val i = b.size / 2
        b[i] = (b[i].toInt() xor 0x55).toByte()
        f.writeBytes(b)
    }

    private fun truncate(f: File) {
        val b = f.readBytes()
        f.writeBytes(b.copyOf(b.size * 6 / 10))
    }

    private fun snapshot(): List<String> =
        tmp.root.walkTopDown().filter { it.isFile }.map { it.path + ":" + it.length() }.sorted().toList()

    private fun assertReason(reason: ArchiveFailure, block: () -> Unit) {
        try {
            block()
            fail("Seharusnya gagal dengan $reason")
        } catch (e: ArchiveException) {
            assertEquals(reason, e.reason)
        }
    }

    @Test fun arsipUtuhLulusDiSemuaFormat() {
        for (fmt in listOf(ArchiveFormat.ZIP, ArchiveFormat.SEVEN_Z, ArchiveFormat.TAR, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_BZ2, ArchiveFormat.TAR_XZ)) {
            val r = ArchiveEngine.test(build(fmt), null, ctl())
            assertTrue("$fmt: ${r.failure}", r.ok)
            assertEquals("$fmt", 64 * 1024L, r.bytes)
            assertTrue("$fmt", r.entries >= 1)
            assertEquals("$fmt", fmt != ArchiveFormat.TAR, r.checksummed)
            assertEquals("$fmt", -1, r.badIndex)
        }
    }

    @Test fun gzBz2XzTunggalUtuhLulus() {
        val files = listOf(
            single("a.gz") { GzipCompressorOutputStream(it) },
            single("b.bz2") { BZip2CompressorOutputStream(it) },
            single("c.xz") { XZCompressorOutputStream(it) },
        )
        for (f in files) {
            val r = ArchiveEngine.test(f, null, ctl())
            assertTrue(f.name, r.ok)
            assertEquals(f.name, 1, r.entries)
            assertEquals(f.name, 64 * 1024L, r.bytes)
        }
    }

    @Test fun byteTengahDiubahTerdeteksiDiFormatBerchecksum() {
        for (fmt in listOf(ArchiveFormat.ZIP, ArchiveFormat.SEVEN_Z, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_BZ2, ArchiveFormat.TAR_XZ)) {
            val a = build(fmt)
            flipMiddle(a)
            val r = ArchiveEngine.test(a, null, ctl())
            assertFalse("$fmt harus terdeteksi rusak", r.ok)
            assertEquals("$fmt", ArchiveFailure.CORRUPT, r.failure)
            assertTrue("$fmt: indeks ${r.badIndex}", r.badIndex >= -1)
        }
    }

    @Test fun zipRusakMelaporkanIndeksEntriPertamaYangRusak() {
        val a = build(ArchiveFormat.ZIP)
        flipMiddle(a)
        val r = ArchiveEngine.test(a, null, ctl())
        assertEquals(ArchiveFailure.CORRUPT, r.failure)
        assertTrue("indeks entri rusak harus diketahui", r.badIndex >= 0)
    }

    @Test fun tarPolosTidakPunyaChecksumIsiDanDijelaskan() {
        val a = build(ArchiveFormat.TAR)
        flipMiddle(a) // byte data entri berubah: TAR polos tidak bisa mendeteksinya
        val r = ArchiveEngine.test(a, null, ctl())
        assertFalse(r.checksummed)
        assertTrue(r.ok)
    }

    @Test fun gzBz2XzTunggalRusakTerdeteksi() {
        val files = listOf(
            single("a.gz") { GzipCompressorOutputStream(it) },
            single("b.bz2") { BZip2CompressorOutputStream(it) },
            single("c.xz") { XZCompressorOutputStream(it) },
        )
        for (f in files) {
            flipMiddle(f)
            val r = ArchiveEngine.test(f, null, ctl())
            assertEquals(f.name, ArchiveFailure.CORRUPT, r.failure)
        }
    }

    @Test fun arsipTerpotongDilaporkanRusak() {
        for (fmt in listOf(ArchiveFormat.ZIP, ArchiveFormat.SEVEN_Z, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_BZ2, ArchiveFormat.TAR_XZ)) {
            val a = build(fmt)
            truncate(a)
            val r = ArchiveEngine.test(a, null, ctl())
            assertEquals("$fmt", ArchiveFailure.CORRUPT, r.failure)
        }
    }

    @Test fun zipBersandiButuhSandiDanSandiSalahDitolak() {
        val a = build(ArchiveFormat.ZIP, pw = "rahasia")
        assertReason(ArchiveFailure.NEED_PASSWORD) { ArchiveEngine.test(a, null, ctl()) }
        assertReason(ArchiveFailure.WRONG_PASSWORD) { ArchiveEngine.test(a, "salah", ctl()) }
        val ok = ArchiveEngine.test(a, "rahasia", ctl())
        assertTrue(ok.ok)
        assertEquals(64 * 1024L, ok.bytes)
    }

    @Test fun dapatDibatalkan() {
        val a = build(ArchiveFormat.ZIP)
        val r = ArchiveEngine.test(a, null, ctl(cancel = { true }))
        assertTrue(r.cancelled)
        assertFalse(r.ok)
        assertNull(r.failure)
    }

    @Test fun tidakMenulisApaPunKeDisk() {
        val a = build(ArchiveFormat.ZIP)
        val b = build(ArchiveFormat.TAR_GZ)
        val before = snapshot()
        ArchiveEngine.test(a, null, ctl())
        ArchiveEngine.test(b, null, ctl())
        assertEquals(before, snapshot())
    }

    @Test fun berkasKosongAtauHilangMelempar() {
        assertReason(ArchiveFailure.NOT_FOUND) { ArchiveEngine.test(File(tmp.root, "tidak-ada.zip"), null, ctl()) }
        val kosong = File(tmp.root, "kosong.zip").also { it.writeBytes(ByteArray(0)) }
        assertReason(ArchiveFailure.CORRUPT) { ArchiveEngine.test(kosong, null, ctl()) }
    }
}
