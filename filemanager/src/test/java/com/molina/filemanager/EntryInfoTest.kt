package com.molina.filemanager

import org.junit.Assert.assertEquals
import org.junit.Test

class EntryInfoTest {
    @Test fun jumlahItemDitulisItem() {
        assertEquals("1 item", EntryInfo.countLabel(1))
        assertEquals("2 item", EntryInfo.countLabel(2))
        assertEquals("120 item", EntryInfo.countLabel(120))
    }

    @Test fun folderKosongDitulisKosong() = assertEquals("Kosong", EntryInfo.countLabel(0))

    @Test fun takDiketahuiTanpaTeks() = assertEquals("", EntryInfo.countLabel(-1))
}
