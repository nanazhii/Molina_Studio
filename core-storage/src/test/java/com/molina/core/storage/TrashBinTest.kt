// molina-patch26h
package com.molina.core.storage

import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaPaths
import com.molina.core.common.errorOrNull
import com.molina.core.common.getOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrashBinTest {
    @get:Rule val tmp = TemporaryFolder()

    private val none = emptySet<File>()
    private val day = 24L * 60 * 60 * 1000

    private fun root(): File = tmp.newFolder("Molina")
    private fun file(parent: File, name: String, text: String = "x"): File =
        File(parent, name).also { it.writeText(text) }
    private fun dir(parent: File, name: String): File = File(parent, name).also { it.mkdirs() }

    @Test fun trashMemindahkanBerkasDanMenyimpanAsalRelatif() {
        val r = root()
        val sub = dir(r, "sub")
        val f = file(sub, "a.txt", "isi")
        val bin = TrashBin(r)
        val items = bin.trash(listOf(f), none, now = 1000L).getOrNull()!!
        assertEquals(1, items.size)
        assertFalse(f.exists())
        assertEquals("a.txt", items[0].name)
        assertEquals("sub/a.txt", items[0].origin)
        assertEquals("sub", items[0].originParent)
        assertEquals("isi", items[0].payload.readText())
        assertEquals(listOf("sub/a.txt"), bin.list().map { it.origin })
    }

    @Test fun trashFolderBerisiLaluRestoreKeAsal() {
        val r = root()
        val d = dir(r, "film")
        file(d, "x.mkv")
        val bin = TrashBin(r)
        val item = bin.trash(listOf(d), none).getOrNull()!!.single()
        assertTrue(item.isDirectory)
        assertFalse(d.exists())
        val back = bin.restore(listOf(item.id)).getOrNull()!!
        assertEquals(listOf("film"), back)
        assertTrue(File(d, "x.mkv").isFile)
        assertTrue(bin.list().isEmpty())
    }

    @Test fun restoreNamaBentrokDiberiNomorTanpaMenimpa() {
        val r = root()
        val f = file(r, "a.txt", "lama")
        val bin = TrashBin(r)
        val item = bin.trash(listOf(f), none).getOrNull()!!.single()
        file(r, "a.txt", "baru")
        val back = bin.restore(listOf(item.id)).getOrNull()!!
        assertEquals(listOf("a (1).txt"), back)
        assertEquals("baru", File(r, "a.txt").readText())
        assertEquals("lama", File(r, "a (1).txt").readText())
    }

    @Test fun restoreMembuatFolderAsalYangHilang() {
        val r = root()
        val sub = dir(dir(r, "a"), "b")
        val f = file(sub, "c.txt")
        val bin = TrashBin(r)
        val item = bin.trash(listOf(f), none).getOrNull()!!.single()
        File(r, "a").deleteRecursively()
        assertEquals(listOf("a/b/c.txt"), bin.restore(listOf(item.id)).getOrNull()!!)
        assertTrue(File(r, "a/b/c.txt").isFile)
    }

    @Test fun itemDilindungiDitolakTanpaMemindahApaPun() {
        val r = root()
        val protectedDir = dir(r, "cinema")
        val ok = file(r, "a.txt")
        val bin = TrashBin(r)
        val e = bin.trash(listOf(ok, protectedDir), setOf(protectedDir)).errorOrNull()
        assertEquals(ErrorKind.PROTECTED, e?.kind)
        assertTrue(ok.exists())
        assertTrue(bin.list().isEmpty())
    }

    @Test fun rootDanFolderSampahSendiriDitolak() {
        val r = root()
        val bin = TrashBin(r)
        assertEquals(ErrorKind.INVALID_PATH, bin.trash(listOf(r), none).errorOrNull()?.kind)
        val f = file(r, "a.txt")
        bin.trash(listOf(f), none)
        assertEquals(ErrorKind.PROTECTED, bin.trash(listOf(File(r, MolinaPaths.TRASH_DIR)), none).errorOrNull()?.kind)
    }

    @Test fun sumberTidakAdaMenggagalkanSemuaTanpaMemindahApaPun() {
        val r = root()
        val ok = file(r, "a.txt")
        val bin = TrashBin(r)
        val e = bin.trash(listOf(ok, File(r, "tidak-ada")), none).errorOrNull()
        assertEquals(ErrorKind.NOT_FOUND, e?.kind)
        assertTrue(ok.exists())
    }

    @Test fun indukDanAnakAtauDuplikatHanyaSatuItem() {
        val r = root()
        val d = dir(r, "d")
        val child = file(d, "k.txt")
        val bin = TrashBin(r)
        val items = bin.trash(listOf(d, child, d), none).getOrNull()!!
        assertEquals(listOf("d"), items.map { it.origin })
        assertEquals(1, bin.list().size)
    }

    @Test fun purgeDanEmptyMenghapusPermanen() {
        val r = root()
        val bin = TrashBin(r)
        val a = bin.trash(listOf(file(r, "a.txt")), none, now = 1L).getOrNull()!!.single()
        bin.trash(listOf(file(r, "b.txt")), none, now = 2L)
        assertEquals(1, bin.purge(listOf(a.id)).getOrNull())
        assertEquals(listOf("b.txt"), bin.list().map { it.origin })
        assertEquals(1, bin.empty().getOrNull())
        assertTrue(bin.list().isEmpty())
    }

    @Test fun purgeExpiredHanyaMenghapusYangLebihTuaDariBatas() {
        val r = root()
        val bin = TrashBin(r)
        bin.trash(listOf(file(r, "lama.txt")), none, now = 0L)
        bin.trash(listOf(file(r, "baru.txt")), none, now = 20 * day)
        assertEquals(0, bin.purgeExpired(now = 29 * day).getOrNull())
        assertEquals(1, bin.purgeExpired(now = 31 * day).getOrNull())
        assertEquals(listOf("baru.txt"), bin.list().map { it.origin })
        // jam mundur: tidak menghapus apa pun
        assertEquals(0, bin.purgeExpired(now = -5 * day).getOrNull())
    }

    @Test fun daysLeftMenurunLaluBerhentiDiNol() {
        val r = root()
        val item = TrashBin(r).trash(listOf(file(r, "a.txt")), none, now = 0L).getOrNull()!!.single()
        assertEquals(30, item.daysLeft(0L))
        assertEquals(20, item.daysLeft(10 * day))
        assertEquals(0, item.daysLeft(99 * day))
        assertEquals(30, item.daysLeft(-day))
    }

    @Test fun restoreIdTidakAdaGagalNotFound() {
        val bin = TrashBin(root())
        assertEquals(ErrorKind.NOT_FOUND, bin.restore(listOf("nope")).errorOrNull()?.kind)
    }

    @Test fun infoRusakDiabaikanDanDibersihkanOlehEmpty() {
        val r = root()
        val bin = TrashBin(r)
        bin.trash(listOf(file(r, "a.txt")), none)
        val bad = dir(File(r, MolinaPaths.TRASH_DIR), "rusak-0")
        File(bad, "info.properties").writeText("name=../../etc\norigin=x\nat=1\n")
        assertEquals(1, bin.list().size)
        assertNotNull(bin.empty().getOrNull())
        assertFalse(bad.exists())
    }
}
