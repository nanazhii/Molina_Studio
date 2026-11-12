package com.molina.files

import com.molina.core.common.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
