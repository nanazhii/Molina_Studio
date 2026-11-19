package com.molina.player.display

import android.app.Activity
import android.content.SharedPreferences
import com.molina.player.Utils

/**
 * Kecerahan layar pemutar yang diatur lewat gestur, dan ingatannya antar sesi.
 * Tanggung jawab tunggal: membaca/menyimpan pengaturan kecerahan dan menerapkannya ke jendela layar.
 * Pengaturan, penyimpanan, penerapan, dan nilai awal gestur semuanya lewat kelas ini (dulu tersebar di lima tempat).
 */
internal class MPVActivityBrightness(private val activity: Activity) {
    private var remember = false

    /** Persen 0..100 yang diminta pemakai; -1 = belum diatur, jendela memakai kecerahan sistem. */
    private var percent = -1

    /** Baca pengaturan remember_brightness dan nilai terakhir yang tersimpan. */
    fun readSettings(prefs: SharedPreferences) {
        remember = prefs.getBoolean("remember_brightness", false)
        percent = if (remember)
            prefs.getInt("last_screen_brightness", -1)
        else
            -1
    }

    /** Tulis nilai terakhir bila diingat; hapus bila tidak. Dipanggil di dalam editor pengaturan milik layar. */
    fun writeSettings(editor: SharedPreferences.Editor) {
        if (remember)
            editor.putInt("last_screen_brightness", percent)
        else
            editor.remove("last_screen_brightness")
    }

    /** Terapkan nilai yang diminta pemakai ke jendela; tidak melakukan apa pun bila belum diatur. */
    fun apply() {
        if (percent == -1)
            return
        val lp = activity.window.attributes
        lp.screenBrightness = percent / 100f
        activity.window.attributes = lp
    }

    /** Kecerahan sekarang (0..1) sebagai nilai awal gestur; 0.5 bila tidak bisa dibaca. */
    fun current(): Float = Utils.getScreenBrightness(activity) ?: 0.5f

    /** Atur ke [newPercent] (0..100) dan terapkan. */
    fun set(newPercent: Int) {
        percent = newPercent
        apply()
    }
}
