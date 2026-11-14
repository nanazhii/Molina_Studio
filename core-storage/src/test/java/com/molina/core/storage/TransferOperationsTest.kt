// molina-patch26c
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
