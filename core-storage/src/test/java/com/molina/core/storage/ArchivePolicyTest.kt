// molina-patch26k4
package com.molina.core.storage

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

class ArchivePolicyTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun ctl(cancel: () -> Boolean = { false }, p: (ArchiveProgress) -> Unit = {}) =
        ArchiveCtl(ArchiveKind.EXTRACT, cancel, p)

    private fun dir(name: String): File = File(tmp.root, name).also { it.mkdirs() }

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

    private fun extract(archive: File, dest: File, policy: ConflictPolicy, only: Set<String>? = null, c: ArchiveCtl = ctl()) =
        ArchiveEngine.extract(archive, dest, ExtractTarget.HERE, null, only, c, false, policy)

    private fun backups(d: File): List<String> =
        d.walkTopDown().filter { it.name.startsWith(".molina-bak-") }.map { it.name }.toList()

    @Test fun renameAdalahBawaanDanMempertahankanBerkasLama() {
        val dest = dir("dest")
        File(dest, "a.txt").writeText("lama")
        val tar = tarOf("p.tar", listOf("a.txt" to "baru"))
        val r = ArchiveEngine.extract(tar, dest, ExtractTarget.HERE, null, null, ctl())
        assertEquals("lama", File(dest, "a.txt").readText())
        assertEquals("baru", File(dest, "a (1).txt").readText())
        assertEquals(1, r.renamed)
        assertEquals(0, r.skipped)
        assertEquals(0, r.replaced)
    }

    @Test fun skipMelewatiYangAdaDanMenghitungKeSkipped() {
        val dest = dir("dest")
        File(dest, "a.txt").writeText("lama")
        val tar = tarOf("p.tar", listOf("a.txt" to "baru", "b.txt" to "bravo"))
        val r = extract(tar, dest, ConflictPolicy.SKIP)
        assertEquals("lama", File(dest, "a.txt").readText())
        assertEquals("bravo", File(dest, "b.txt").readText())
        assertFalse(File(dest, "a (1).txt").exists())
        assertEquals(1, r.files)
        assertEquals(1, r.skipped)
        assertEquals(0, r.renamed)
    }

    @Test fun skipSemuaEntriTerpilihBukanTidakAdaYangCocok() {
        val dest = dir("dest")
        File(dest, "a.txt").writeText("lama")
        val tar = tarOf("p.tar", listOf("a.txt" to "baru", "b.txt" to "bravo"))
        val r = extract(tar, dest, ConflictPolicy.SKIP, only = setOf("a.txt"))
        assertEquals(0, r.files)
        assertEquals(1, r.skipped)
        assertEquals("lama", File(dest, "a.txt").readText())
    }

    @Test fun overwriteMenggantiBerkasDanTidakMeninggalkanCadangan() {
        val dest = dir("dest")
        File(dest, "a.txt").writeText("lama")
        val tar = tarOf("p.tar", listOf("a.txt" to "baru"))
        val r = extract(tar, dest, ConflictPolicy.OVERWRITE)
        assertEquals("baru", File(dest, "a.txt").readText())
        assertEquals(1, r.replaced)
        assertEquals(0, r.renamed)
        assertEquals(1, r.files)
        assertTrue("cadangan harus dihapus setelah sukses", backups(dest).isEmpty())
        assertEquals(listOf("a.txt"), dest.list()!!.sorted())
    }

    @Test fun overwriteGagalDiTengahMengembalikanBerkasLama() {
        val dest = dir("dest")
        File(dest, "a.txt").writeText("lama")
        val tar = tarOf("jahat.tar", listOf("a.txt" to "baru", "../jahat.txt" to "x"))
        try {
            extract(tar, dest, ConflictPolicy.OVERWRITE)
            fail("Seharusnya gagal dengan UNSAFE_ENTRY")
        } catch (e: ArchiveException) {
            assertEquals(ArchiveFailure.UNSAFE_ENTRY, e.reason)
        }
        assertEquals("lama", File(dest, "a.txt").readText())
        assertEquals(listOf("a.txt"), dest.list()!!.sorted())
        assertFalse(File(tmp.root, "jahat.txt").exists())
    }

    @Test fun overwriteDibatalkanMengembalikanBerkasLama() {
        val dest = dir("dest")
        File(dest, "a.txt").writeText("lama")
        val big = "x".repeat(3 * 1024 * 1024)
        val tar = tarOf("besar.tar", listOf("a.txt" to big))
        var cancelNow = false
        val r = extract(tar, dest, ConflictPolicy.OVERWRITE, c = ctl(cancel = { cancelNow }, p = { cancelNow = true }))
        assertTrue(r.cancelled)
        assertEquals("lama", File(dest, "a.txt").readText())
        assertEquals(listOf("a.txt"), dest.list()!!.sorted())
    }

    @Test fun overwriteTidakMenimpaFolderDenganBerkas() {
        val dest = dir("dest")
        File(dest, "a.txt").mkdirs()
        File(dest, "a.txt/isi.txt").writeText("isi")
        val tar = tarOf("p.tar", listOf("a.txt" to "baru"))
        val r = extract(tar, dest, ConflictPolicy.OVERWRITE)
        assertTrue(File(dest, "a.txt").isDirectory)
        assertEquals("isi", File(dest, "a.txt/isi.txt").readText())
        assertEquals("baru", File(dest, "a (1).txt").readText())
        assertEquals(1, r.renamed)
        assertEquals(0, r.replaced)
    }

    @Test fun overwriteTidakMenulisMelaluiTautanSimbolik() {
        val dest = dir("dest")
        val outside = File(tmp.root, "luar.txt").also { it.writeText("rahasia") }
        val link = File(dest, "a.txt")
        val made = runCatching { Files.createSymbolicLink(link.toPath(), outside.toPath()) }.isSuccess
        assumeTrue("tautan simbolik tidak didukung di lingkungan ini", made)
        val tar = tarOf("p.tar", listOf("a.txt" to "baru"))
        val r = extract(tar, dest, ConflictPolicy.OVERWRITE)
        assertEquals("rahasia", outside.readText())
        assertTrue(Files.isSymbolicLink(link.toPath()))
        assertEquals("baru", File(dest, "a (1).txt").readText())
        assertEquals(1, r.renamed)
        assertEquals(0, r.replaced)
    }

    @Test fun entriKembarDalamSatuArsipTidakMenimpaHasilSendiri() {
        val dest = dir("dest")
        val tar = tarOf("kembar.tar", listOf("a.txt" to "satu", "a.txt" to "dua"))
        val r = extract(tar, dest, ConflictPolicy.OVERWRITE)
        assertEquals("satu", File(dest, "a.txt").readText())
        assertEquals("dua", File(dest, "a (1).txt").readText())
        assertEquals(1, r.renamed)
        assertEquals(0, r.replaced)
    }

    @Test fun skipDanOverwriteBerlakuDiSemuaFormat() {
        for (fmt in listOf(ArchiveFormat.ZIP, ArchiveFormat.SEVEN_Z, ArchiveFormat.TAR_GZ)) {
            val src = File(tmp.root, "src-${fmt.name}").also { it.mkdirs() }
            File(src, "a.txt").writeText("baru")
            val out = File(tmp.root, "out-${fmt.name}").also { it.mkdirs() }
            val archive = ArchiveEngine.compress(
                listOf(src), out, CompressOptions(fmt, fmt.defaultLevel, "paket"), ArchiveCtl(ArchiveKind.COMPRESS, { false }, {}),
            ).file!!
            for (policy in listOf(ConflictPolicy.SKIP, ConflictPolicy.OVERWRITE)) {
                val dest = File(tmp.root, "dest-${fmt.name}-${policy.name}").also { it.mkdirs() }
                File(dest, src.name).mkdirs()
                File(dest, src.name + "/a.txt").writeText("lama")
                val r = extract(archive, dest, policy)
                val expected = if (policy == ConflictPolicy.SKIP) "lama" else "baru"
                assertEquals("$fmt $policy", expected, File(dest, src.name + "/a.txt").readText())
                if (policy == ConflictPolicy.SKIP) assertEquals("$fmt", 1, r.skipped) else assertEquals("$fmt", 1, r.replaced)
                assertTrue("$fmt $policy: tidak boleh ada cadangan", backups(dest).isEmpty())
            }
        }
    }
}
