package com.molina.log

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileLogSinkTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun writesFormattedLine() {
        val sink = FileLogSink(tmp.root)
        MolinaLogger(listOf(sink), clock = { 0L }).i(MolinaLogger.tag("term", "s1"), "halo")
        sink.flush()
        val text = tmp.root.resolve("molina.log").readText()
        assertTrue(text.contains("I/Molina/term/s1: halo"))
        sink.close()
    }

    @Test
    fun rotatesWhenTooLarge() {
        val sink = FileLogSink(tmp.root, maxBytes = 50, maxFiles = 3)
        val log = MolinaLogger(listOf(sink))
        repeat(10) { log.i("Molina/test", "baris nomor $it dengan isi cukup panjang") }
        sink.flush()
        assertTrue(tmp.root.resolve("molina.1.log").exists())
        sink.close()
    }
}
