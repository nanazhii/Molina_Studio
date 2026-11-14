// molina-patch26k
package com.molina.core.storage

import java.io.File

/** Pemeriksaan ruang kosong. Nilai 0 dari sistem (penuh atau tidak diketahui) tidak dianggap bukti kekurangan. */
internal object DiskSpace {
    const val MIN_FREE = 8L * 1024 * 1024

    /** true bila ruang kosong di [dir] jelas kurang dari [needed] ditambah cadangan [MIN_FREE]. */
    fun lacks(dir: File, needed: Long): Boolean {
        val free = dir.usableSpace
        if (free <= 0L) return false
        val need = if (needed > Long.MAX_VALUE - MIN_FREE) Long.MAX_VALUE else needed + MIN_FREE
        return free < need
    }
}
