// molina-patch21
package com.molina.terminal.proot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NanoProtocolTest {
    @Test
    fun titlesAndScriptKinds() {
        assertTrue(NanoProtocol.isEditorTitle("nano: a.py"))
        assertFalse(NanoProtocol.isEditorTitle("nano-run: a.py"))
        assertTrue(NanoProtocol.isRunDialogTitle("nano-run: a.py"))
        assertEquals("a.py", NanoProtocol.fileName("nano: a.py"))
        assertTrue(NanoProtocol.isScript("a.py"))
        assertTrue(NanoProtocol.isScript("run.SH"))
        assertFalse(NanoProtocol.isScript("catatan.txt"))
        assertFalse(NanoProtocol.isScript("py"))
        assertFalse(NanoProtocol.isScript("(baru)"))
    }

    @Test
    fun detectsSaveAndNamePrompts() {
        assertEquals(NanoProtocol.Mode.EDIT, NanoProtocol.detectEditPrompt(listOf("", "  GNU nano", "")))
        assertEquals(NanoProtocol.Mode.SAVE, NanoProtocol.detectEditPrompt(listOf("Save modified buffer?  (Y/N)")))
        assertEquals(NanoProtocol.Mode.NAME, NanoProtocol.detectEditPrompt(listOf("File Name to Write: x.py")))
        assertEquals(NanoProtocol.Mode.NAME, NanoProtocol.detectEditPrompt(listOf("Write to File: x.py")))
    }

    @Test
    fun parsesRunChoicesWithDefault() {
        val rows = listOf("┌ Molina · periksa skrip: a.py", "└ Pilih [1/2/a/3/e/x] (Enter = 1): ")
        val c = NanoProtocol.parseRunChoices(rows)!!
        assertEquals(listOf('1', '2', 'a', '3', 'e', 'x'), c.map { it.key })
        assertEquals('1', c.first { it.isDefault }.key)
        assertEquals("1\r", NanoProtocol.sequenceOf(c.first()))
        assertEquals("PERBAIKI", c.first().label)
    }

    @Test
    fun parsesWrappedPromptAndRejectsOffered() {
        assertNotNull(NanoProtocol.parseRunChoices(listOf("└ Pilih [3/e/x] (Enter", " = 3): ")))
        assertNull(NanoProtocol.parseRunChoices(listOf("Pilih [1/2/9/3/x] (Enter = 1)"))) // token tak sah
        assertNull(NanoProtocol.parseRunChoices(listOf("Pilih [1/2] (Enter = 1)"))) // tanpa 3 dan x
        assertNull(NanoProtocol.parseRunChoices(listOf("tidak ada prompt")))
    }
}
