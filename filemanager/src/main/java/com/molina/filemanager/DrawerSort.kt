// molina-27g2
package com.molina.filemanager

/** [molina-27g2] Kunci urut tab Berkas. [defaultAscending]: arah saat kunci baru dipilih (Tanggal/Ukuran: terbesar dulu). */
enum class DrawerSortKey(val label: String, val defaultAscending: Boolean) {
    NAME("Nama", true),
    DATE("Tanggal", false),
    SIZE("Ukuran", false),
    TYPE("Tipe", true),
}

/**
 * [molina-27g2] Urutan isi folder: folder selalu di atas, lalu [key] menurut [ascending]; seri dipecah nama (naik,
 * tanpa peduli huruf besar/kecil). Diterapkan per tingkat pada tree. Murni Kotlin agar bisa diuji JVM.
 */
data class DrawerSort(val key: DrawerSortKey = DrawerSortKey.NAME, val ascending: Boolean = true) {
    val label: String get() = key.label + " " + (if (ascending) "\u25B2" else "\u25BC")

    /** Memilih kunci aktif membalik arah; kunci lain memakai arah bawaannya. */
    fun choose(k: DrawerSortKey): DrawerSort =
        if (k == key) copy(ascending = !ascending) else DrawerSort(k, k.defaultAscending)

    fun encode(): String = key.name + ":" + (if (ascending) "asc" else "desc")

    fun comparator(): Comparator<DrawerEntry> = Comparator { a, b ->
        if (a.isDir != b.isDir) return@Comparator if (a.isDir) -1 else 1
        var c = when (key) {
            DrawerSortKey.NAME -> String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name)
            DrawerSortKey.DATE -> a.modified.compareTo(b.modified)
            DrawerSortKey.SIZE -> a.size.compareTo(b.size)
            DrawerSortKey.TYPE -> extensionOf(a).compareTo(extensionOf(b))
        }
        if (!ascending) c = -c
        if (c != 0) return@Comparator c
        val n = String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name)
        if (n != 0) n else a.name.compareTo(b.name)
    }

    companion object {
        val DEFAULT = DrawerSort()

        fun decode(s: String?): DrawerSort {
            val p = s?.split(':') ?: return DEFAULT
            if (p.size != 2) return DEFAULT
            val k = DrawerSortKey.values().firstOrNull { it.name == p[0] } ?: return DEFAULT
            return when (p[1]) {
                "asc" -> DrawerSort(k, true)
                "desc" -> DrawerSort(k, false)
                else -> DEFAULT
            }
        }

        /** Ekstensi huruf kecil; folder dan nama berawal titik tanpa titik lain = kosong. */
        fun extensionOf(e: DrawerEntry): String {
            if (e.isDir) return ""
            val i = e.name.lastIndexOf('.')
            return if (i <= 0) "" else e.name.substring(i + 1).lowercase(java.util.Locale.ROOT)
        }

        fun sorted(list: List<DrawerEntry>, sort: DrawerSort): List<DrawerEntry> = list.sortedWith(sort.comparator())
    }
}
