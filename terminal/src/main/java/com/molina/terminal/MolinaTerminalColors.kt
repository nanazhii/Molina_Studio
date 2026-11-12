// molina-patch8
package com.molina.terminal

import android.content.Context
import androidx.core.content.ContextCompat
import com.molina.core.ui.R as UiR
import com.molina.terminal.emulator.TerminalColors
import java.util.Properties

/**
 * Skema warna terminal dari token core-ui. Wajib dipanggil SEBELUM sesi dibuat: emulator menyalin
 * skema ini saat dibangun. Palet ANSI 16 warna: aksen teal Molina, kontras aman di latar gelap.
 */
internal object MolinaTerminalColors {

    private val ansi = intArrayOf(
        0x1F252B, 0xFF6B6B, 0x4DD0A1, 0xFFB300, 0x5C9DFF, 0xC792EA, 0x00BFA5, 0xC9D1D9,
        0x3A434C, 0xFF8A8A, 0x7BE0B8, 0xFFD166, 0x82B4FF, 0xDDA9F5, 0x3DE0C8, 0xF5F7FA,
    )

    fun apply(context: Context) {
        val props = Properties()
        props.setProperty("background", hex(ContextCompat.getColor(context, UiR.color.molina_bg)))
        props.setProperty("foreground", hex(ContextCompat.getColor(context, UiR.color.molina_on_surface)))
        props.setProperty("cursor", hex(ContextCompat.getColor(context, UiR.color.molina_terminal_accent)))
        ansi.forEachIndexed { i, c -> props.setProperty("color$i", hex(c)) }
        TerminalColors.COLOR_SCHEME.updateWith(props)
    }

    private fun hex(color: Int): String = String.format("#%06X", color and 0xFFFFFF)
}
