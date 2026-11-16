// molina-27l
package com.molina.filemanager

import com.molina.filemanager.EmbeddedMenu.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Embedded27lTest {
    private fun entry(a: Action, label: String = a.name) = EmbeddedMenu.Entry(a, label)

    private fun header(showHidden: Boolean = false) = EmbeddedMenu.build(
        EmbeddedMenu.State(
            targetCount = 0, hasActions = true, favoriteAvailable = true, isFavorite = false,
            trashAvailable = true, global = true, showHidden = showHidden,
        ),
    )

    @Test fun setiapAksenPunyaGlyph() {
        for (a in Action.values()) assertTrue(EmbeddedMenuIcons.glyph(entry(a)).file.startsWith("ic_mnu_"))
    }

    @Test fun berkasVektorAdaUntukSetiapGlyph() {
        val dir = File("src/main/res/drawable")
        for (g in EmbeddedMenuIcons.Glyph.values()) assertTrue(g.file, File(dir, g.file + ".xml").isFile)
    }

    @Test fun labelPintasanTanpaAwalanLambang() {
        assertEquals("Molina", EmbeddedMenuIcons.label(entry(Action.GO_MOLINA, "\u2630 \u2605 Molina")))
        assertEquals("Download", EmbeddedMenuIcons.label(entry(Action.GO_DOWNLOAD, "\u2630 Download")))
        assertEquals("Favorit", EmbeddedMenuIcons.label(entry(Action.FAVORITES_LIST, "\u2630 Favorit")))
        assertEquals("Salin path", EmbeddedMenuIcons.label(entry(Action.COPY_PATH, "Salin path")))
    }

    @Test fun labelTersembunyiDipersingkatDanIkonMengikutiKeadaan() {
        val show = header(false).first { it.action == Action.TOGGLE_HIDDEN }
        val hide = header(true).first { it.action == Action.TOGGLE_HIDDEN }
        assertEquals("Tampilkan tersembunyi", EmbeddedMenuIcons.label(show))
        assertEquals("Sembunyikan tersembunyi", EmbeddedMenuIcons.label(hide))
        assertEquals(EmbeddedMenuIcons.Glyph.EYE, EmbeddedMenuIcons.glyph(show))
        assertEquals(EmbeddedMenuIcons.Glyph.EYE_OFF, EmbeddedMenuIcons.glyph(hide))
    }

    @Test fun kelompokLokasiTepatDelapanDanBerurutanDiAkhirMenuHeader() {
        val a = header().map { it.action }
        val nav = a.map { EmbeddedMenuIcons.isNav(it) }
        assertEquals(8, nav.count { it })
        assertTrue(nav.takeLast(8).all { it })
        assertFalse(nav.dropLast(8).any { it })
    }

    @Test fun hapusDanHapusPermanenBerbedaIkon() {
        assertFalse(EmbeddedMenuIcons.glyph(entry(Action.TRASH)) == EmbeddedMenuIcons.glyph(entry(Action.DELETE_PERMANENT)))
    }
}
