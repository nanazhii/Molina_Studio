// molina-patch26j
package com.molina.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareMimeTest {
    @Test fun sama() {
        assertEquals("video/mp4", ShareMime.common(listOf("video/mp4", "video/mp4")))
    }

    @Test fun jenisIndukSama() {
        assertEquals("video/*", ShareMime.common(listOf("video/mp4", "video/x-matroska")))
    }

    @Test fun campuran() {
        assertEquals("*/*", ShareMime.common(listOf("video/mp4", "text/plain")))
    }

    @Test fun kosong() {
        assertEquals("*/*", ShareMime.common(emptyList()))
    }

    @Test fun ekstensiTambahan() {
        assertEquals("video/x-matroska", ShareMime.forExtension("MKV"))
        assertNull(ShareMime.forExtension("zzz"))
    }
}
