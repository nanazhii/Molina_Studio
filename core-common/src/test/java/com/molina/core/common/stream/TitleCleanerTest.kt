package com.molina.core.common.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleCleanerTest {
    @Test fun facebookStatistikHashtagDanPengunggahDibuang() {
        val raw = "204K views \u00b7 4.5K reactions | Dibilang suruh luruskan, eh rakyat malah yang meluruskan " +
            "\uD83E\uDD23\uD83E\uDD23 Tuhkannn tambah ribet kan? \uD83D\uDE02 #fbpro #komediviral #fyp | Rahidin Yusuf"
        assertEquals(
            "Dibilang suruh luruskan, eh rakyat malah yang meluruskan \uD83E\uDD23\uD83E\uDD23 Tuhkannn tambah\u2026",
            TitleCleaner.clean(raw),
        )
    }

    @Test fun captionPendekFacebookBersih() {
        assertEquals("Lucu banget", TitleCleaner.clean("1.2M views \u00b7 30K reactions | Lucu banget \uD83D\uDE02 #fyp | Akun A"))
        assertEquals("Resep cepat", TitleCleaner.clean("204 rb tayangan | Resep cepat | Dapur Ibu"))
    }

    @Test fun tikTokHanyaHashtagDiUjung() {
        assertEquals("Tutorial sambal", TitleCleaner.clean("Tutorial sambal #masak #fyp #viral"))
    }

    @Test fun hashtagDiTengahTinggalKatanya() {
        assertEquals("Kucing 1 paling lucu", TitleCleaner.clean("Kucing #1 paling lucu #cat"))
        assertEquals("Hari ini fyp banget", TitleCleaner.clean("Hari ini #fyp banget"))
    }

    @Test fun judulBiasaDenganGarisTegakTidakDisentuh() {
        assertEquals("Bagian 1 | Bagian 2", TitleCleaner.clean("Bagian 1 | Bagian 2"))
    }

    @Test fun hanyaHashtagTetapMenghasilkanTeks() {
        assertEquals("fyp viral", TitleCleaner.clean("#fyp #viral"))
    }

    @Test fun kosongDanNull() {
        assertNull(TitleCleaner.clean(null))
        assertNull(TitleCleaner.clean("   \n\t "))
    }

    @Test fun spasiDanBarisBaruDirapatkan() {
        assertEquals("Satu dua tiga", TitleCleaner.clean("  Satu\n dua\t\ttiga  "))
    }

    @Test fun panjangDipotongDiBatasKata() {
        val out = TitleCleaner.clean("kata ".repeat(40))!!
        assertTrue(out.length <= TitleCleaner.MAX_LEN)
        assertTrue(out.endsWith("\u2026"))
    }

    @Test fun tidakMemotongPasanganSurrogate() {
        val out = TitleCleaner.clean("a".repeat(TitleCleaner.MAX_LEN - 2) + "\uD83D\uDE02\uD83D\uDE02 akhir")!!
        assertTrue(out.endsWith("\u2026"))
        assertTrue(!Character.isHighSurrogate(out[out.length - 2]))
    }
}
