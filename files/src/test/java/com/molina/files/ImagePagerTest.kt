// molina-patch26k5h:t02
package com.molina.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ImagePagerTest {
    private val list = listOf("a.jpg", "b.png", "c.webp")

    @Test fun mulaiDariGambarYangDiketuk() {
        val p = ImagePager.of(list, "b.png")!!
        assertEquals("b.png", p.current)
        assertEquals("2 / 3", p.label())
    }

    @Test fun tidakAdaAtauKosongMenghasilkanNull() {
        assertNull(ImagePager.of(list, "z.jpg"))
        assertNull(ImagePager.of(emptyList(), "a.jpg"))
    }

    @Test fun ujungTidakBerputar() {
        assertNull(ImagePager.of(list, "a.jpg")!!.prev())
        assertNull(ImagePager.of(list, "c.webp")!!.next())
    }

    @Test fun majuMundurBerjalan() {
        val p = ImagePager.of(list, "b.png")!!
        assertEquals("c.webp", p.next()!!.current)
        assertEquals("a.jpg", p.prev()!!.current)
    }

    @Test fun penunjukTakBerubah() {
        val p = ImagePager.of(list, "b.png")!!
        p.next()
        assertEquals("b.png", p.current)
    }

    @Test fun menelusuriSemuaDariAwalKeAkhir() {
        var p: ImagePager? = ImagePager.of(list, "a.jpg")
        val seen = ArrayList<String>()
        while (p != null) {
            seen.add(p.current)
            p = p.next()
        }
        assertEquals(list, seen)
    }

    @Test fun satuGambarTanpaTetangga() {
        val p = ImagePager.of(listOf("x.png"), "x.png")!!
        assertNotNull(p)
        assertNull(p.next())
        assertNull(p.prev())
        assertEquals("1 / 1", p.label())
    }

    @Test fun geserKiriBerikutnyaGeserKananSebelumnya() {
        assertEquals(1, ImagePager.swipe(-200f, 10f, 100f))
        assertEquals(-1, ImagePager.swipe(200f, -10f, 100f))
    }

    @Test fun geserPendekDiabaikan() {
        assertEquals(0, ImagePager.swipe(-99f, 0f, 100f))
        assertEquals(0, ImagePager.swipe(0f, 0f, 100f))
    }

    @Test fun geserCondongVertikalDiabaikan() {
        assertEquals(0, ImagePager.swipe(-200f, 200f, 100f))
        assertEquals(0, ImagePager.swipe(150f, 120f, 100f))
        assertEquals(1, ImagePager.swipe(-300f, 150f, 100f))
    }
}
