// molina-patch9a
package com.molina.studio

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver

/**
 * Menyembunyikan bottom nav selama keyboard tampil, supaya tidak ikut terdorong ke atas oleh
 * adjustResize dan ruang terminal lebih lega. Deteksi: decor view tidak mengecil saat adjustResize,
 * sedangkan tepi bawah area tampak naik ke atas keyboard. Menyembunyikan nav tidak mengubah ukuran
 * decor, jadi tidak ada osilasi tampil-sembunyi.
 */
internal class KeyboardNavHider private constructor(
    private val content: View,
    private val nav: View,
) {
    private val visibleFrame = Rect()

    private fun update() {
        val root = content.rootView
        root.getWindowVisibleDisplayFrame(visibleFrame)
        val target = if (isKeyboardVisible(root.height, visibleFrame.bottom)) View.GONE else View.VISIBLE
        if (nav.visibility != target) nav.visibility = target
    }

    companion object {
        private const val THRESHOLD = 0.15f

        /** Keyboard dianggap tampil bila lebih dari 15% tinggi layar tertutup (bilah navigasi sistem jauh di bawahnya). */
        fun isKeyboardVisible(rootHeight: Int, visibleBottom: Int): Boolean =
            rootHeight > 0 && rootHeight - visibleBottom > rootHeight * THRESHOLD

        /** Pasang pada [activity]; pendengar hidup selama view tree Activity. */
        fun install(activity: Activity, nav: View) {
            val content = activity.findViewById<View>(android.R.id.content)
            val hider = KeyboardNavHider(content, nav)
            content.viewTreeObserver.addOnGlobalLayoutListener(ViewTreeObserver.OnGlobalLayoutListener { hider.update() })
        }
    }
}
