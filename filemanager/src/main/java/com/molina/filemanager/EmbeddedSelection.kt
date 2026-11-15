// molina-27e
package com.molina.filemanager

/**
 * [molina-27e] Keadaan mode pilih tab Berkas. Murni Kotlin (tanpa Android) agar bisa diuji JVM.
 * Tidak ikut dipakai Terminal. Hanya dimutasi di main thread.
 */
internal class EmbeddedSelection {
    private val items = LinkedHashSet<String>()

    var active: Boolean = false
        private set

    val count: Int get() = items.size
    val paths: List<String> get() = items.toList()

    fun contains(path: String): Boolean = path in items

    /** [molina-27h] Geser kanan: pilih [path] (mode pilih otomatis aktif). */
    fun select(path: String) {
        active = true
        items.add(path)
    }

    /** [molina-27h] Geser kiri: batal pilih [path]. Mode pilih tetap aktif walau kosong. */
    fun deselect(path: String) {
        items.remove(path)
    }

    /** Masuk mode pilih; [first] (bila ada) langsung terpilih. */
    fun enter(first: String? = null) {
        active = true
        if (first != null) items.add(first)
    }

    /** Membalik status [path]; true bila kini terpilih. Mode pilih tetap aktif walau kosong. */
    fun toggle(path: String): Boolean {
        if (!items.remove(path)) items.add(path)
        return path in items
    }

    fun selectAll(all: Collection<String>) {
        active = true
        items.clear()
        items.addAll(all)
    }

    fun exit() {
        active = false
        items.clear()
    }

    companion object {
        /** Buang path yang induknya ikut terpilih, supaya folder dan isinya tidak diproses dua kali. */
        fun topLevel(paths: Collection<String>): List<String> {
            val set = paths.toSet()
            return paths.distinct().filter { p ->
                set.none { a -> a != p && p.startsWith(a.trimEnd('/') + "/") }
            }
        }
    }
}

/** [molina-27e] Isi papan klip Salin/Pindah: mode + path absolut sumber. */
internal class ClipState {
    var mode: TransferMode? = null
        private set
    var paths: List<String> = emptyList()
        private set

    val count: Int get() = paths.size

    fun put(mode: TransferMode, paths: List<String>) {
        this.mode = mode
        this.paths = paths.distinct()
    }

    fun clear() {
        mode = null
        paths = emptyList()
    }
}

/**
 * [molina-27e] Papan klip tingkat proses: MainActivity mengganti Fragment tiap pindah tab, jadi klip harus
 * bertahan di luar View agar Salin di satu kunjungan bisa Tempel di kunjungan berikutnya.
 */
internal object EmbeddedClipboard {
    val state = ClipState()
}
