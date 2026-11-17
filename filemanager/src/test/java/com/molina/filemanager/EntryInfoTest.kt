package com.molina.filemanager

import com.molina.core.ui.EntryInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class EntryInfoTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int, zone: ZoneId = utc): Long =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

    private val now = ms(2026, 11, 16, 17, 30)

    @Test fun jumlahItemDitulisItem() {
        assertEquals("1 item", EntryInfo.countLabel(1))
        assertEquals("2 item", EntryInfo.countLabel(2))
        assertEquals("120 item", EntryInfo.countLabel(120))
    }

    @Test fun folderKosongDitulisKosong() = assertEquals("Kosong", EntryInfo.countLabel(0))

    @Test fun takDiketahuiTanpaTeks() = assertEquals("", EntryInfo.countLabel(-1))

    @Test fun hariIniHanyaJam() {
        assertEquals("17.29", EntryInfo.dateLabel(ms(2026, 11, 16, 17, 29), now, utc))
        assertEquals("00.05", EntryInfo.dateLabel(ms(2026, 11, 16, 0, 5), now, utc))
    }

    @Test fun kemarinDenganJam() = assertEquals("Kemarin 23.59", EntryInfo.dateLabel(ms(2026, 11, 15, 23, 59), now, utc))

    @Test fun lebihLamaTanggalPenuh() {
        assertEquals("14/11/26 08.00", EntryInfo.dateLabel(ms(2026, 11, 14, 8, 0), now, utc))
        assertEquals("31/12/25 23.00", EntryInfo.dateLabel(ms(2025, 12, 31, 23, 0), now, utc))
    }

    @Test fun tidakValidKosong() {
        assertEquals("", EntryInfo.dateLabel(0L, now, utc))
        assertEquals("", EntryInfo.dateLabel(-5L, now, utc))
    }

    @Test fun masaDepanJauhTanggalPenuh() = assertEquals("18/11/26 09.00", EntryInfo.dateLabel(ms(2026, 11, 18, 9, 0), now, utc))

    @Test fun zonaWaktuMenentukanPergantianHari() {
        val bali = ZoneId.of("Asia/Makassar") // UTC+8: 17.30 UTC sudah 01.30 tanggal berikutnya
        assertEquals("01.29", EntryInfo.dateLabel(ms(2026, 11, 16, 17, 29), now, bali))
        assertEquals("Kemarin 23.59", EntryInfo.dateLabel(ms(2026, 11, 16, 15, 59), now, bali))
    }

    @Test fun segarDalam24Jam() {
        assertTrue(EntryInfo.isFresh(now - 60_000L, now))
        assertTrue(EntryInfo.isFresh(now - EntryInfo.FRESH_WINDOW_MS, now))
        assertFalse(EntryInfo.isFresh(now - EntryInfo.FRESH_WINDOW_MS - 1L, now))
    }

    @Test fun nolTidakSegar() = assertFalse(EntryInfo.isFresh(0L, now))

    @Test fun selisihJamPerangkatKecilTetapSegar() {
        assertTrue(EntryInfo.isFresh(now + 30_000L, now))
        assertFalse(EntryInfo.isFresh(now + 2 * 3_600_000L, now))
    }

    @Test fun segarTapiBerlabelKemarinLintasTengahMalam() {
        val n = ms(2026, 11, 16, 0, 10)
        val m = ms(2026, 11, 15, 23, 50)
        assertEquals("Kemarin 23.50", EntryInfo.dateLabel(m, n, utc))
        assertTrue(EntryInfo.isFresh(m, n))
    }

    @Test fun acuanBukaPertamaTanpaPenanda() = assertEquals(0L, EntryInfo.baselineFor(0L, now))

    @Test fun acuanMemakaiWaktuBukaTerakhir() {
        val last = now - 3_600_000L
        assertEquals(last, EntryInfo.baselineFor(last, now))
    }

    @Test fun acuanDibatasiTujuhHari() {
        val last = now - 30L * 24L * 3_600_000L
        assertEquals(now - EntryInfo.NEW_LOOKBACK_MS, EntryInfo.baselineFor(last, now))
    }

    @Test fun baruSejakDibukaTerakhir() {
        val base = now - 3_600_000L
        assertTrue(EntryInfo.isNewSince(now - 60_000L, base))
        assertFalse(EntryInfo.isNewSince(base - 1L, base))
        assertFalse(EntryInfo.isNewSince(base, base)) // sama persis: bukan baru
    }

    @Test fun tanpaAcuanTidakAdaYangBaru() = assertFalse(EntryInfo.isNewSince(now, 0L))

    @Test fun perubahanSendiriDiabaikan() {
        val base = now - 3_600_000L
        val quiet = now - 1_000L
        assertFalse(EntryInfo.isNewSince(now - 2_000L, base, quiet)) // dibuat sebelum operasi lokal selesai
        assertTrue(EntryInfo.isNewSince(now, base, quiet)) // datang dari luar setelah operasi lokal
    }
}
