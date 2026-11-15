// molina-27g3
package com.molina.filemanager

/**
 * [molina-27g3] Logika murni Cari: semua kata (dipisah spasi) harus ada di nama, tanpa peduli huruf besar/kecil
 * (aturan yang sama dengan EntryLister di :core-storage). Murni Kotlin agar bisa diuji JVM.
 */
internal object SearchMatch {
    fun words(query: String): List<String> = query.trim().split(' ').filter { it.isNotEmpty() }

    fun matches(name: String, query: String): Boolean = words(query).all { name.contains(it, ignoreCase = true) }

    /** Rentang (inklusif) teks cocok di [name]; terurut, dan digabung bila bertumpuk atau bersambung. */
    fun ranges(name: String, query: String): List<IntRange> {
        val raw = ArrayList<IntRange>()
        for (w in words(query)) {
            var i = name.indexOf(w, 0, ignoreCase = true)
            while (i >= 0) {
                raw.add(i until i + w.length)
                i = name.indexOf(w, i + w.length, ignoreCase = true)
            }
        }
        if (raw.isEmpty()) return emptyList()
        raw.sortBy { it.first }
        val out = ArrayList<IntRange>()
        var cur = raw[0]
        for (k in 1 until raw.size) {
            val r = raw[k]
            if (r.first <= cur.last + 1) {
                if (r.last > cur.last) cur = cur.first..r.last
            } else {
                out.add(cur)
                cur = r
            }
        }
        out.add(cur)
        return out
    }

    /** Teks ringkas di bawah kolom cari. */
    fun info(count: Int, truncated: Boolean, deep: Boolean): String {
        if (count == 0) return "Tidak ada hasil"
        if (truncated) return "$count hasil (dibatasi), persempit kata kunci"
        return if (deep) "$count hasil di subfolder" else "$count hasil"
    }
}
