// molina-27s
package com.molina.files

import org.junit.Assert.assertEquals
import org.junit.Test

class BerkasLokasiTest {
    @Test
    fun rootIsInternal() = assertEquals("Internal", BerkasPaths.lokasi(""))

    @Test
    fun subfolderIsPrefixed() = assertEquals("Internal/Download/hasil", BerkasPaths.lokasi("Download/hasil"))
}
