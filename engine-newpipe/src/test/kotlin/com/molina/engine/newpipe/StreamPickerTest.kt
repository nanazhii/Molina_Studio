package com.molina.engine.newpipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamPickerTest {
    @Test fun expireQuery() {
        assertEquals(1_800_000_000_000L, StreamPicker.expiresAtMs("x/v?a=1&expire=1800000000&b=2"))
    }

    @Test fun expirePath() {
        assertEquals(1_800_000_000_000L, StreamPicker.expiresAtMs("x/expire/1800000000/ip/1"))
    }

    @Test fun expireTidakMasukAkal() {
        assertNull(StreamPicker.expiresAtMs("x/v?expire=123456789012"))
        assertNull(StreamPicker.expiresAtMs("x/v?a=1"))
    }

    @Test fun petaGalat() {
        assertEquals(com.molina.core.common.StreamError.NETWORK, NewPipeStreamResolver.map(java.io.IOException("x")))
        assertEquals(com.molina.core.common.StreamError.UNKNOWN, NewPipeStreamResolver.map(IllegalStateException()))
    }
}
