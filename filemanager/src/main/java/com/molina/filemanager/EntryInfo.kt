// molina-28a
package com.molina.filemanager

/** [molina-28a] Teks keterangan baris Berkas. Murni Kotlin agar bisa diuji JVM. */
object EntryInfo {
    /** Jumlah isi folder: "2 item", "Kosong" bila 0, kosong bila tidak diketahui (negatif). */
    fun countLabel(count: Int): String = when {
        count < 0 -> ""
        count == 0 -> "Kosong"
        else -> "$count item"
    }
}
