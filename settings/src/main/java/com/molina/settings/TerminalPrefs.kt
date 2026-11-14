// molina-patch15
package com.molina.settings

import android.content.Context

/** Preferensi terminal (SharedPreferences). Dibaca app saat membuat sesi Debian. */
object TerminalPrefs {
    private const val FILE = "molina_terminal"
    private const val KEY_SHOW_ROOT = "prompt_show_root"
    private const val KEY_NANO_OPEN = "nano_keys_open"
    private const val KEY_RUN_MODE = "run_mode"
    private const val KEY_RUN_LOG = "run_log"
    const val RUN_ASK = "ask"
    const val RUN_ACTIVE = "active"
    const val RUN_NEW = "new"

    fun showRoot(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_SHOW_ROOT, false)

    fun setShowRoot(ctx: Context, value: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_SHOW_ROOT, value).apply()
    }

    fun nanoKeysOpen(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_NANO_OPEN, false)

    fun setNanoKeysOpen(ctx: Context, value: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_NANO_OPEN, value).apply()
    }

    /** Perilaku tombol Jalankan di drawer berkas: [RUN_ASK] (bawaan), [RUN_ACTIVE], atau [RUN_NEW]. */
    fun runMode(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_RUN_MODE, RUN_ASK) ?: RUN_ASK

    fun setRunMode(ctx: Context, value: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_RUN_MODE, value).apply()
    }

    /** Salin keluaran Jalankan ke Molina/run-logs (bawaan: mati). */
    fun runLog(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_RUN_LOG, false)

    fun setRunLog(ctx: Context, value: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_RUN_LOG, value).apply()
    }
}
