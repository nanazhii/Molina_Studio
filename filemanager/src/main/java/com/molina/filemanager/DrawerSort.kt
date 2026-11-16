// molina-27k
package com.molina.filemanager

/** [molina-27k] Urutan daftar berkas. Folder selalu di atas (seperti file manager Android); murni, diuji JVM. */
enum class DrawerSort(val label: String) {
    NAME_ASC("Nama (A-Z)"),
    NAME_DESC("Nama (Z-A)"),
    DATE_DESC("Terbaru dulu"),
    DATE_ASC("Terlama dulu"),
    SIZE_DESC("Terbesar dulu"),
    SIZE_ASC("Terkecil dulu"),
    TYPE_ASC("Jenis (A-Z)"),
    ;

    companion object {
        fun comparator(sort: DrawerSort): Comparator<DrawerEntry> {
            val ci = String.CASE_INSENSITIVE_ORDER
            val byName = Comparator<DrawerEntry> { a, b -> ci.compare(a.name, b.name) }
            val primary: Comparator<DrawerEntry> = when (sort) {
                NAME_ASC -> byName
                NAME_DESC -> Comparator { a, b -> ci.compare(b.name, a.name) }
                DATE_DESC -> compareByDescending<DrawerEntry> { it.modified }.then(byName)
                DATE_ASC -> compareBy<DrawerEntry> { it.modified }.then(byName)
                SIZE_DESC -> compareByDescending<DrawerEntry> { it.size }.then(byName)
                SIZE_ASC -> compareBy<DrawerEntry> { it.size }.then(byName)
                TYPE_ASC -> compareBy<DrawerEntry, String>(ci) { it.name.substringAfterLast('.', "") }.then(byName)
            }
            return Comparator { a, b ->
                if (a.isDir != b.isDir) (if (a.isDir) -1 else 1) else primary.compare(a, b)
            }
        }
    }
}
