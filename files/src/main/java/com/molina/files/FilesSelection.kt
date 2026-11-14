// molina-patch26c
package com.molina.files

import com.molina.core.storage.TransferKind

/**
 * Keadaan Mode Pilih (murni, tanpa Android). Kunci item = path relatif terhadap root Molina.
 *
 * Aturan: geser kanan memilih (dan masuk Mode Pilih bila belum aktif), geser kiri membatalkan, dan
 * Mode Pilih keluar otomatis saat jumlah terpilih kembali 0. Satu-satunya keadaan aktif dengan 0
 * terpilih adalah saat mode dimasuki lewat menu "Pilih" dan belum ada yang dipilih.
 */
internal class FilesSelection {
    var active: Boolean = false
        private set

    private val keys = LinkedHashSet<String>()

    val count: Int get() = keys.size

    fun contains(key: String): Boolean = key in keys

    fun snapshot(): List<String> = keys.toList()

    /** Masuk Mode Pilih tanpa memilih apa pun (menu "Pilih"). */
    fun enter() {
        active = true
    }

    /** Geser kanan: masuk mode bila perlu lalu centang. true bila ada perubahan. */
    fun select(key: String): Boolean {
        active = true
        return keys.add(key)
    }

    /** Geser kiri: batal centang; keluar otomatis bila jadi kosong. true bila ada perubahan. */
    fun deselect(key: String): Boolean {
        if (!keys.remove(key)) return false
        if (keys.isEmpty()) active = false
        return true
    }

    /** Ketuk baris saat mode aktif. Mengembalikan true bila item kini terpilih. */
    fun toggle(key: String): Boolean {
        if (key in keys) deselect(key) else select(key)
        return key in keys
    }

    fun selectAll(all: Collection<String>) {
        active = true
        keys.addAll(all)
    }

    /** Keluar dari Mode Pilih dan mengosongkan pilihan. */
    fun exit() {
        keys.clear()
        active = false
    }

    /**
     * Membuang pilihan yang tidak lagi tampil (mis. setelah dihapus). Bila sebelumnya ada pilihan dan
     * kini kosong, mode keluar otomatis. Mode kosong dari menu tidak ikut keluar. true bila berubah.
     */
    fun retainOnly(visible: Set<String>): Boolean {
        val hadAny = keys.isNotEmpty()
        val changed = keys.retainAll(visible)
        if (hadAny && keys.isEmpty()) active = false
        return changed
    }

    fun restore(active: Boolean, saved: Collection<String>) {
        keys.clear()
        keys.addAll(saved)
        this.active = active || keys.isNotEmpty()
    }
}

/** Isi papan klip internal Berkas: item (path relatif) yang menunggu ditempel. [kind] COPY atau MOVE. */
internal data class FilesClip(val kind: TransferKind, val paths: List<String>)
