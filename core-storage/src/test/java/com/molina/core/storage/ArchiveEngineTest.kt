// molina-patch26k
package com.molina.core.storage

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
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
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveEngineTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun ctl(kind: ArchiveKind = ArchiveKind.EXTRACT, cancel: () -> Boolean = { false }, p: (ArchiveProgress) -> Unit = {}) =
        ArchiveCtl(kind, cancel, p)

    private fun dir(name: String): File = File(tmp.root, name).also { it.mkdirs() }

    private fun sample(): File {
        val src = dir("src")
        File(src, "a.txt").writeText("alpha")
        File(src, "sub").mkdirs()
        File(src, "sub/b.txt").writeText("bravo")
        return src
    }

    private fun assertReason(reason: ArchiveFailure, block: () -> Unit) {
        try {
            block()
            fail("Seharusnya gagal dengan $reason")
        } catch (e: ArchiveException) {
            assertEquals(reason, e.reason)
        }
    }

    private fun compress(src: List<File>, out: File, fmt: ArchiveFormat, base: String = "paket", pw: String? = null, level: Int = fmt.defaultLevel) =
        ArchiveEngine.compress(src, out, CompressOptions(fmt, level, base, pw), ctl(ArchiveKind.COMPRESS))

    private fun extract(archive: File, dest: File, target: ExtractTarget = ExtractTarget.HERE, pw: String? = null, only: Set<String>? = null, c: ArchiveCtl = ctl()) =
        ArchiveEngine.extract(archive, dest, target, pw, only, c)

    private fun tarOf(name: String, entries: List<Pair<String, String>>): File {
        val f = File(tmp.root, name)
        TarArchiveOutputStream(FileOutputStream(f)).use { t ->
            for ((n, text) in entries) {
                val e = TarArchiveEntry(n)
                val b = text.toByteArray()
                e.size = b.size.toLong()
                t.putArchiveEntry(e)
                t.write(b)
                t.closeArchiveEntry()
            }
        }
        return f
    }

    private fun roundTrip(fmt: ArchiveFormat) {
        val src = sample()
        val out = dir("out")
        val archive = compress(listOf(src), out, fmt).file!!
        assertTrue(archive.name, archive.name.endsWith(fmt.ext))
        val listing = ArchiveEngine.list(archive, null) { false }
        assertEquals(2, listing.fileCount)
        assertEquals(2, listing.folderCount)
        assertFalse(listing.encrypted)
        val dest = dir("dest")
        val r = extract(archive, dest, ExtractTarget.NAMED_FOLDER)
        assertFalse(r.cancelled)
        assertEquals("paket", r.dir!!.name)
        assertEquals(2, r.files)
        assertEquals("alpha", File(r.dir, "src/a.txt").readText())
        assertEquals("bravo", File(r.dir, "src/sub/b.txt").readText())
    }

    @Test fun zipBolakBalik() = roundTrip(ArchiveFormat.ZIP)
    @Test fun sevenZBolakBalik() = roundTrip(ArchiveFormat.SEVEN_Z)
    @Test fun tarBolakBalik() = roundTrip(ArchiveFormat.TAR)
    @Test fun tarGzBolakBalik() = roundTrip(ArchiveFormat.TAR_GZ)
    @Test fun tarBz2BolakBalik() = roundTrip(ArchiveFormat.TAR_BZ2)
    @Test fun tarXzBolakBalik() = roundTrip(ArchiveFormat.TAR_XZ)

    @Test fun zipTanpaKompresiDanNamaUnicode() {
        val src = dir("src")
        File(src, "caf\u00e9 \u65e5\u672c.txt").writeText("isi")
        val archive = compress(listOf(src), dir("out"), ArchiveFormat.ZIP, level = 0).file!!
        val r = extract(archive, dir("dest"), ExtractTarget.HERE)
        assertEquals("isi", File(dir("dest"), "src/caf\u00e9 \u65e5\u672c.txt").readText())
        assertEquals(1, r.files)
    }

    @Test fun zipBersandiAes() {
        val src = sample()
        val archive = compress(listOf(src), dir("out"), ArchiveFormat.ZIP, pw = "kunci123").file!!
        val listing = ArchiveEngine.list(archive, null) { false }
        assertTrue(listing.encrypted)
        val dest = dir("dest")
        assertReason(ArchiveFailure.NEED_PASSWORD) { extract(archive, dest, ExtractTarget.NAMED_FOLDER) }
        assertReason(ArchiveFailure.WRONG_PASSWORD) { extract(archive, dest, ExtractTarget.NAMED_FOLDER, pw = "salah") }
        assertTrue("tidak boleh ada sisa", dest.listFiles()!!.isEmpty())
        val r = extract(archive, dest, ExtractTarget.NAMED_FOLDER, pw = "kunci123")
        assertEquals("alpha", File(r.dir, "src/a.txt").readText())
    }

    @Test fun passwordHanyaUntukZip() {
        val src = sample()
        for (fmt in listOf(ArchiveFormat.SEVEN_Z, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR)) {
            assertReason(ArchiveFailure.PASSWORD_UNSUPPORTED) { compress(listOf(src), dir("out"), fmt, pw = "x") }
        }
        assertTrue("tidak boleh ada keluaran", dir("out").listFiles()!!.isEmpty())
    }

    @Test fun zipSlipDitolakSebelumMenulis() {
        val z = File(tmp.root, "jahat.zip")
        ZipOutputStream(FileOutputStream(z)).use { zo ->
            zo.putNextEntry(ZipEntry("ok/a.txt")); zo.write("x".toByteArray()); zo.closeEntry()
            zo.putNextEntry(ZipEntry("../jahat.txt")); zo.write("x".toByteArray()); zo.closeEntry()
        }
        val dest = dir("dest")
        assertReason(ArchiveFailure.UNSAFE_ENTRY) { extract(z, dest) }
        assertFalse(File(tmp.root, "jahat.txt").exists())
        assertTrue(dest.listFiles()!!.isEmpty())
    }

    @Test fun tarSlipDitolakDanRollbackTermasukFolder() {
        val t = tarOf("jahat.tar", listOf("d/e/a.txt" to "x", "../jahat.txt" to "x"))
        val dest = dir("dest")
        assertReason(ArchiveFailure.UNSAFE_ENTRY) { extract(t, dest) }
        assertFalse(File(tmp.root, "jahat.txt").exists())
        assertTrue("folder yang dibuat harus ikut dihapus", dest.listFiles()!!.isEmpty())
    }

    @Test fun tidakMenimpaBerkasYangAda() {
        val src = dir("src")
        File(src, "a.txt").writeText("baru")
        val archive = compress(listOf(File(src, "a.txt")), dir("out"), ArchiveFormat.TAR).file!!
        val dest = dir("dest")
        File(dest, "a.txt").writeText("lama")
        val r = extract(archive, dest, ExtractTarget.HERE)
        assertEquals(1, r.renamed)
        assertEquals("lama", File(dest, "a.txt").readText())
        assertEquals("baru", File(dest, "a (1).txt").readText())
    }

    @Test fun namaFolderHasilUnik() {
        val archive = compress(listOf(sample()), dir("out"), ArchiveFormat.TAR_GZ).file!!
        val dest = dir("dest")
        val a = extract(archive, dest, ExtractTarget.NAMED_FOLDER)
        val b = extract(archive, dest, ExtractTarget.NAMED_FOLDER)
        assertEquals("paket", a.dir!!.name)
        assertEquals("paket (1)", b.dir!!.name)
    }

    @Test fun hanyaEntriTerpilih() {
        val t = tarOf("x.tar", listOf("a.txt" to "1", "sub/b.txt" to "2", "subx/c.txt" to "3"))
        val dest = dir("dest")
        val r = extract(t, dest, only = setOf("sub/"))
        assertEquals(1, r.files)
        assertTrue(File(dest, "sub/b.txt").exists())
        assertFalse(File(dest, "a.txt").exists())
        assertFalse(File(dest, "subx").exists())
        assertReason(ArchiveFailure.NO_MATCH) { extract(t, dir("dest2"), only = setOf("tidak-ada.txt")) }
    }

    @Test fun namaEntriTerlaluPanjangDalamByte() {
        val long = tarOf("p.tar", listOf("a".repeat(300) to "x"))
        assertReason(ArchiveFailure.NAME_TOO_LONG) { extract(long, dir("d1")) }
        val unicode = tarOf("u.tar", listOf("\u00e9".repeat(130) to "x")) // 260 byte
        assertReason(ArchiveFailure.NAME_TOO_LONG) { extract(unicode, dir("d2")) }
    }

    @Test fun jumlahEntriDibatasi() {
        val t = tarOf("banyak.tar", listOf("1" to "x", "2" to "x", "3" to "x"))
        val old = ArchiveEngine.maxEntries
        ArchiveEngine.maxEntries = 2
        try {
            val dest = dir("dest")
            assertReason(ArchiveFailure.TOO_MANY_ENTRIES) { extract(t, dest) }
            assertTrue(dest.listFiles()!!.isEmpty())
        } finally {
            ArchiveEngine.maxEntries = old
        }
    }

    @Test fun tautanDanBerkasKhususTarDilewatiDanDihitung() {
        val f = File(tmp.root, "t.tar")
        TarArchiveOutputStream(FileOutputStream(f)).use { t ->
            val e = TarArchiveEntry("a.txt")
            e.size = 1
            t.putArchiveEntry(e); t.write('x'.code); t.closeArchiveEntry()
            val l = TarArchiveEntry("tautan", TarArchiveEntry.LF_SYMLINK)
            l.linkName = "/etc/passwd"
            t.putArchiveEntry(l); t.closeArchiveEntry()
        }
        val dest = dir("dest")
        val r = extract(f, dest)
        assertEquals(1, r.files)
        assertEquals(1, r.skipped)
        assertFalse(File(dest, "tautan").exists())
    }

    @Test fun pembatalanMembersihkanSemuaHasil() {
        val src = dir("src")
        File(src, "besar.bin").writeBytes(ByteArray(3 * 1024 * 1024) { (it % 251).toByte() })
        val archive = compress(listOf(src), dir("out"), ArchiveFormat.TAR).file!!
        val dest = dir("dest")
        var cancelNow = false
        val r = extract(archive, dest, c = ctl(cancel = { cancelNow }, p = { cancelNow = true }))
        assertTrue(r.cancelled)
        assertNull(r.dir)
        assertTrue("tidak boleh ada sisa", dest.listFiles()!!.isEmpty())
    }

    @Test fun pembatalanKompresMenghapusKeluaran() {
        val src = dir("src")
        File(src, "besar.bin").writeBytes(ByteArray(3 * 1024 * 1024) { (it % 251).toByte() })
        val out = dir("out")
        var cancelNow = false
        val c = ArchiveCtl(ArchiveKind.COMPRESS, { cancelNow }, { cancelNow = true })
        val r = ArchiveEngine.compress(listOf(src), out, CompressOptions(ArchiveFormat.ZIP, 6, "paket"), c)
        assertTrue(r.cancelled)
        assertTrue(out.listFiles()!!.isEmpty())
    }

    @Test fun kemajuanDilaporkanSampaiSelesai() {
        val src = dir("src")
        File(src, "besar.bin").writeBytes(ByteArray(1024 * 1024))
        val seen = ArrayList<ArchiveProgress>()
        val c = ArchiveCtl(ArchiveKind.COMPRESS, { false }, { seen.add(it) })
        ArchiveEngine.compress(listOf(src), dir("out"), CompressOptions(ArchiveFormat.TAR, 0, "p"), c)
        assertTrue(seen.isNotEmpty())
        assertEquals(1f, seen.last().fraction, 0.001f)
    }

    @Test fun kompresMenolakNamaDanSumberBermasalah() {
        val src = sample()
        val out = dir("out")
        assertReason(ArchiveFailure.INVALID_NAME) { compress(listOf(src), out, ArchiveFormat.ZIP, base = "a/b") }
        assertReason(ArchiveFailure.INVALID_NAME) { compress(listOf(src), out, ArchiveFormat.ZIP, base = " ") }
        assertReason(ArchiveFailure.NAME_TOO_LONG) { compress(listOf(src), out, ArchiveFormat.ZIP, base = "x".repeat(300)) }
        assertReason(ArchiveFailure.EMPTY_SOURCE) { compress(emptyList(), out, ArchiveFormat.ZIP) }
        assertReason(ArchiveFailure.NOT_FOUND) { compress(listOf(File(tmp.root, "hilang")), out, ArchiveFormat.ZIP) }
        assertReason(ArchiveFailure.UNSUPPORTED) { compress(listOf(src), out, ArchiveFormat.GZ) }
        val d1 = dir("d1"); File(d1, "sama.txt").writeText("1")
        val d2 = dir("d2"); File(d2, "sama.txt").writeText("2")
        assertReason(ArchiveFailure.NAME_CONFLICT) {
            compress(listOf(File(d1, "sama.txt"), File(d2, "sama.txt")), out, ArchiveFormat.ZIP)
        }
        assertTrue("tidak boleh ada keluaran", out.listFiles()!!.isEmpty())
    }

    @Test fun keluaranTidakMenimpa() {
        val src = sample()
        val out = dir("out")
        assertEquals("paket.tar.gz", compress(listOf(src), out, ArchiveFormat.TAR_GZ).file!!.name)
        assertEquals("paket (1).tar.gz", compress(listOf(src), out, ArchiveFormat.TAR_GZ).file!!.name)
    }

    @Test fun tautanSimbolikDilewatiDanDihitung() {
        val src = sample()
        Files.createSymbolicLink(File(src, "tautan").toPath(), File(src, "a.txt").toPath())
        val r = compress(listOf(src), dir("out"), ArchiveFormat.ZIP)
        assertEquals(1, r.skipped)
        val listing = ArchiveEngine.list(r.file!!, null) { false }
        assertEquals(2, listing.fileCount)
        assertReason(ArchiveFailure.EMPTY_SOURCE) {
            compress(listOf(File(src, "tautan")), dir("out2"), ArchiveFormat.ZIP)
        }
    }

    @Test fun arsipDikompresDiDalamSumberTidakMemasukkanDirinya() {
        val src = sample()
        val r = compress(listOf(src), src, ArchiveFormat.ZIP)
        val listing = ArchiveEngine.list(r.file!!, null) { false }
        assertEquals(2, listing.fileCount)
    }

    @Test fun arsipRusakDanKosongDilaporkanJelas() {
        for (name in listOf("x.zip", "x.7z", "x.tar.gz", "x.tar.xz")) {
            val f = File(tmp.root, name)
            f.writeBytes(ByteArray(2048) { (it * 31).toByte() })
            assertReason(ArchiveFailure.CORRUPT) { ArchiveEngine.list(f, null) { false } }
        }
        val kosong = File(tmp.root, "kosong.zip").also { it.createNewFile() }
        assertReason(ArchiveFailure.CORRUPT) { ArchiveEngine.list(kosong, null) { false } }
        assertReason(ArchiveFailure.NOT_FOUND) { ArchiveEngine.list(File(tmp.root, "hilang.zip"), null) { false } }
        assertReason(ArchiveFailure.UNSUPPORTED) {
            ArchiveEngine.list(File(tmp.root, "x.txt").also { it.writeText("a") }, null) { false }
        }
    }

    @Test fun gzTunggalDiekstrakKeNamaStem() {
        val src = dir("src")
        val plain = File(src, "catatan.txt").also { it.writeText("halo") }
        val gz = File(tmp.root, "catatan.txt.gz")
        java.util.zip.GZIPOutputStream(FileOutputStream(gz)).use { it.write(plain.readBytes()) }
        val listing = ArchiveEngine.list(gz, null) { false }
        assertEquals("catatan.txt", listing.entries.single().name)
        val dest = dir("dest")
        val r = extract(gz, dest, ExtractTarget.HERE)
        assertEquals(1, r.files)
        assertEquals("halo", File(dest, "catatan.txt").readText())
    }

    @Test fun daftarIsiBisaDibatalkan() {
        val archive = compress(listOf(sample()), dir("out"), ArchiveFormat.TAR_GZ).file!!
        try {
            ArchiveEngine.list(archive, null) { true }
            fail("Seharusnya dibatalkan")
        } catch (e: java.util.concurrent.CancellationException) {
            assertNotNull(e)
        }
    }
}
