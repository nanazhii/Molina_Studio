// molina-patch9
package com.molina.terminal

/**
 * Status tombol modifier virtual. Ketuk = sekali pakai; tekan lama = kunci.
 * Hanya diakses dari main thread. [listener] dipakai UI untuk menggambar ulang tombol.
 */
internal class TerminalModifiers {

    var ctrl = false
        private set
    var alt = false
        private set
    var ctrlLocked = false
        private set
    var altLocked = false
        private set

    var listener: (() -> Unit)? = null

    fun tapCtrl() {
        if (ctrlLocked) {
            ctrlLocked = false
            ctrl = false
        } else {
            ctrl = !ctrl
        }
        listener?.invoke()
    }

    fun tapAlt() {
        if (altLocked) {
            altLocked = false
            alt = false
        } else {
            alt = !alt
        }
        listener?.invoke()
    }

    fun toggleCtrlLock() {
        ctrlLocked = !ctrlLocked
        ctrl = ctrlLocked
        listener?.invoke()
    }

    fun toggleAltLock() {
        altLocked = !altLocked
        alt = altLocked
        listener?.invoke()
    }

    /** Dipanggil setelah satu karakter terkirim: modifier sekali pakai dilepas, yang terkunci tetap. */
    fun consumeOneShot() {
        var changed = false
        if (ctrl && !ctrlLocked) {
            ctrl = false
            changed = true
        }
        if (alt && !altLocked) {
            alt = false
            changed = true
        }
        if (changed) listener?.invoke()
    }
}
