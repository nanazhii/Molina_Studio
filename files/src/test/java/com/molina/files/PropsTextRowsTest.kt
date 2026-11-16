// molina-27v
package com.molina.files

import com.molina.core.storage.FileProps
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class PropsTextRowsTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test fun barisLabelNilai() {
        val p = FileProps("a.mkv", "Movies/a.mkv", false, 10, 0, 0, 0L, true, true, false)
        val rows = PropsText.rows(listOf(p), utc, PropsText.View("Internal", "/storage/emulated/0"))
        assertEquals("Jenis" to "Berkas MKV", rows[0])
        assertEquals("Path" to "/storage/emulated/0/Movies/a.mkv", rows.last())
    }
}
