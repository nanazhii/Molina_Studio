package com.molina.core.common.picker

import android.content.Context
import java.io.File
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LocalPickerRegistryTest {
    private val fake = object : LocalPicker {
        override fun pick(context: Context, kind: PickKind, startDir: File?, onResult: (File?) -> Unit) = onResult(null)
    }

    @After fun bersih() = LocalPickerRegistry.install(null)

    @Test fun awalnyaKosong() = assertNull(LocalPickerRegistry.get())

    @Test fun terpasangDanDicabut() {
        LocalPickerRegistry.install(fake)
        assertSame(fake, LocalPickerRegistry.get())
        LocalPickerRegistry.install(null)
        assertNull(LocalPickerRegistry.get())
    }
}
