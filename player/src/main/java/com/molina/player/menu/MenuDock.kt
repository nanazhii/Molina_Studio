package com.molina.player.menu

import android.app.Activity
import android.view.Gravity
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog

/**
 * Penempatan dialog menu bergaya strip: menempel di pojok kanan-bawah dengan lebar tetap, dan tingginya
 * dibatasi agar sisanya bisa di-scroll (tepi pudar = petunjuk). Tanggung jawab tunggal: jendela dialog.
 */
internal object MenuDock {
    /** Tempel dialog ke pojok kanan-bawah; lebar [widthDp] (maks 92% layar), margin kecil dari tepi. */
    fun dockBottomEnd(activity: Activity, dialog: AlertDialog, widthDp: Int, marginDp: Int = 8) {
        val w = dialog.window ?: return
        val dm = activity.resources.displayMetrics
        val px = (widthDp * dm.density).toInt().coerceAtMost((dm.widthPixels * 0.92f).toInt())
        val margin = (marginDp * dm.density).toInt()
        w.setGravity(Gravity.BOTTOM or Gravity.END)
        w.setLayout(px, WindowManager.LayoutParams.WRAP_CONTENT)
        val lp = w.attributes
        lp.x = margin
        lp.y = margin
        w.attributes = lp
    }

    /** Batasi tinggi dialog ke [maxHeightDp] (maks 70% layar); isi yang lebih tinggi di-scroll. */
    fun capHeight(activity: Activity, dialog: AlertDialog, maxHeightDp: Int) {
        val dm = activity.resources.displayMetrics
        val cap = minOf((maxHeightDp * dm.density).toInt(), (dm.heightPixels * 0.7f).toInt())
        val w = dialog.window ?: return
        if (w.decorView.height > cap) w.setLayout(w.attributes.width, cap)
    }
}
