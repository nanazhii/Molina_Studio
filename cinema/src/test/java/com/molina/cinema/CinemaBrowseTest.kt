package com.molina.cinema

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CinemaBrowseTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun isMedia_mengenaliEkstensiTanpaMembedakanHuruf() {
        assertTrue(CinemaBrowse.isMedia("film.MKV"))
        assertTrue(CinemaBrowse.isMedia("lagu.flac"))
        assertFalse(CinemaBrowse.isMedia("catatan.txt"))
        assertFalse(CinemaBrowse.isMedia("tanpaekstensi"))
    }

    @Test fun list_folderDuluLaluMediaTanpaBerkasTersembunyi() {
        val d = tmp.newFolder("cinema")
        File(d, "b.mp4").writeText("x")
        File(d, "A.mkv").writeText("x")
        File(d, "readme.txt").writeText("x")
        File(d, ".rahasia.mp4").writeText("x")
        File(d, "zFolder").mkdir()
        File(d, "aFolder").mkdir()
        val names = CinemaBrowse.list(d).map { it.name }
        assertEquals(listOf("aFolder", "zFolder", "A.mkv", "b.mp4"), names)
    }

    @Test fun list_folderTakAdaMengembalikanKosong() {
        assertTrue(CinemaBrowse.list(File(tmp.root, "tidak-ada")).isEmpty())
    }

    @Test fun startDir_memilihMolinaCinemaBilaAda() {
        assertEquals(tmp.root, CinemaBrowse.startDir(tmp.root))
        val c = File(tmp.root, "Molina/cinema").also { it.mkdirs() }
        assertEquals(c, CinemaBrowse.startDir(tmp.root))
    }

    @Test fun parentWithin_tidakKeluarDariRoot() {
        val sub = File(tmp.root, "a/b").also { it.mkdirs() }
        assertEquals(File(tmp.root, "a").canonicalPath, CinemaBrowse.parentWithin(sub, tmp.root)!!.canonicalPath)
        assertNull(CinemaBrowse.parentWithin(tmp.root, tmp.root))
    }

    @Test fun recent_terbaruDiAtasTanpaDuplikatDanDibatasi() {
        var l = emptyList<String>()
        for (i in 1..12) l = CinemaRecent.push(l, "/p/$i.mp4")
        assertEquals(CinemaRecent.MAX, l.size)
        assertEquals("/p/12.mp4", l.first())
        l = CinemaRecent.push(l, "/p/5.mp4")
        assertEquals("/p/5.mp4", l.first())
        assertEquals(1, l.count { it == "/p/5.mp4" })
        assertEquals(CinemaRecent.MAX, l.size)
    }

    @Test fun recent_encodeDecodeBolakBalik() {
        val l = listOf("/a/b.mp4", "/c d/e.mkv")
        assertEquals(l, CinemaRecent.decode(CinemaRecent.encode(l)))
        assertTrue(CinemaRecent.decode(null).isEmpty())
        assertTrue(CinemaRecent.decode("").isEmpty())
    }
}
