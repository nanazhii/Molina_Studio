// molina-patch15
package com.molina.settings

import android.content.Context

/** Preferensi terminal (SharedPreferences). Dibaca app saat membuat sesi Debian. */
object TerminalPrefs {
    private const val FILE = "molina_terminal"
    private const val KEY_SHOW_ROOT = "prompt_show_root"

    fun showRoot(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_SHOW_ROOT, false)

    fun setShowRoot(ctx: Context, value: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_SHOW_ROOT, value).apply()
    }
}
