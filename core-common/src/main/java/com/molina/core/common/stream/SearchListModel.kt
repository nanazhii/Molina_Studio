package com.molina.core.common.stream

import java.util.Locale

/**
 * Keadaan daftar hasil pencarian: nomor tetap, saring, lompat ke nomor, seleksi, dan paging.
 * Murni Kotlin tanpa Android, sehingga bisa diuji JVM. Tampilan hanya membaca model ini dan meneruskan input.
 */
class SearchListModel(cards: List<SearchCard>) {
    /** Satu baris: [number] mulai dari 1 dan tidak berubah saat daftar disaring. */
    data class Row(val number: Int, val card: SearchCard)

    private val all: List<Row> = cards.mapIndexed { i, c -> Row(i + 1, c) }
    private var query: String = ""

    /** Baris yang sedang tampil (hasil saringan). */
    var rows: List<Row> = all
        private set

    /** Nomor baris terpilih; 0 bila tidak ada baris. */
    var selected: Int = if (all.isEmpty()) 0 else 1
        private set

    val total: Int get() = all.size

    /** Penghitung `003/020` (terpilih / seluruh hasil). */
    fun counter(): String = String.format(Locale.ROOT, "%03d/%03d", selected, total)

    /**
     * Teks kosong = semua; angka = lompat ke nomor itu (daftar tidak disaring); teks lain = saring judul atau meta.
     * Bila baris terpilih tidak lagi tampil, sorotan pindah ke baris pertama yang tampil.
     */
    fun setQuery(raw: String) {
        query = raw.trim()
        val numeric = isNumber(query)
        rows = if (query.isEmpty() || numeric) all else all.filter { matches(it, query) }
        val jump = if (numeric) query.toIntOrNull() else null
        if (jump != null && rows.any { it.number == jump }) {
            selected = jump
        } else if (rows.none { it.number == selected }) {
            selected = rows.firstOrNull()?.number ?: 0
        }
    }

    /** Indeks baris terpilih pada [rows]; -1 bila tidak ada. */
    fun indexOfSelected(): Int = rows.indexOfFirst { it.number == selected }

    /** Menyorot [number]; false (tanpa perubahan) bila nomor itu tidak sedang tampil. */
    fun select(number: Int): Boolean {
        if (rows.none { it.number == number }) return false
        selected = number
        return true
    }

    /** Menggeser sorotan sejauh [delta] baris dan mengembalikan indeks barunya pada [rows]; -1 bila daftar kosong. */
    fun page(delta: Int): Int {
        if (rows.isEmpty()) return -1
        val idx = (indexOfSelected().coerceAtLeast(0) + delta).coerceIn(0, rows.lastIndex)
        selected = rows[idx].number
        return idx
    }

    /**
     * Indeks (pada daftar asli) yang dipilih lewat Enter: nomor yang diketik; atau satu-satunya baris yang tampil;
     * atau baris yang sedang disorot. Null bila tidak ada yang bisa dipilih.
     */
    fun pick(): Int? {
        val number = when {
            isNumber(query) -> query.toIntOrNull()?.takeIf { it in 1..total }
            rows.size == 1 -> rows[0].number
            else -> selected.takeIf { n -> rows.any { it.number == n } }
        }
        return number?.minus(1)
    }

    private fun matches(row: Row, q: String): Boolean =
        row.card.title.contains(q, ignoreCase = true) ||
            (row.card.meta?.contains(q, ignoreCase = true) ?: false)

    private fun isNumber(s: String) = s.isNotEmpty() && s.all { it in '0'..'9' }

    companion object {
        /** Lompatan tombol Sebelumnya/Berikut. */
        const val PAGE_SIZE = 5
    }
}
