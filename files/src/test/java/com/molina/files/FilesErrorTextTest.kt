package com.molina.files

import com.molina.core.common.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertFalse

class FilesErrorTextTest {
    @Test
    fun everyKindHasDistinctNonBlankText() {
        val texts = ErrorKind.values().map { FilesErrorText.of(it) }
        assertTrue(texts.all { it.isNotBlank() })
        assertEquals(texts.size, texts.toSet().size)
    }

    @Test
    fun permissionTextMentionsPermission() {
        assertTrue(FilesErrorText.of(ErrorKind.PERMISSION_DENIED).contains("Izin"))
    }

    // molina-patch26b
    @Test
    fun semuaJenisGalatPunyaTeksTanpaPath() {
        for (kind in ErrorKind.values()) {
            assertFalse(kind.name, FilesErrorText.of(kind).isBlank())
            assertFalse(kind.name, FilesErrorText.forOp(kind).isBlank())
        }
    }

    @Test
    fun teksOperasiTulis() {
        assertEquals("Nama sudah dipakai di folder ini", FilesErrorText.forOp(ErrorKind.ALREADY_EXISTS))
        assertEquals("Operasi gagal (periksa izin tulis)", FilesErrorText.forOp(ErrorKind.IO))
    }
}
