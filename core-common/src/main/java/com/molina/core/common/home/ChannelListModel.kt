package com.molina.core.common.home

import java.util.Locale

/**
 * Keadaan daftar Saluran bergaya TV digital: nomor tetap (001..), saring nama/grup, lompat ke nomor,
 * sorotan, dan paging seperti CH+/CH-. Murni Kotlin tanpa Android, jadi bisa diuji JVM.
 * Tampilan hanya membaca model ini dan meneruskan input.
 */
class ChannelListModel(channels: List<Channel>) {
    /** Satu baris: [number] mulai dari 1 dan tidak berubah saat daftar disaring. */
    data class Row(val number: Int, val channel: Channel)

    private val all: List<Row> = channels.mapIndexed { i, c -> Row(i + 1, c) }
    private var query: String = ""

    /** Baris yang sedang tampil (hasil saringan). */
    var rows: List<Row> = all
        private set

    /** Nomor baris tersorot; 0 bila daftar kosong. */
    var selected: Int = if (all.isEmpty()) 0 else 1
        private set

    val total: Int get() = all.size

    /** Penghitung `003/026` (tersorot / seluruh saluran). */
    fun counter(): String = String.format(Locale.ROOT, "%03d/%03d", selected, total)

    /**
     * Teks kosong = semua; angka = lompat ke nomor itu (daftar tidak disaring); teks lain = saring nama atau grup.
     * Bila baris tersorot tidak lagi tampil, sorotan pindah ke baris pertama yang tampil.
     */
    fun setQuery(raw: String) {
        query = raw.trim()
        val numeric = isNumber(query)
        rows = if (query.isEmpty() || numeric) all else filtered(query)
        val jump = if (numeric) query.toIntOrNull() else null
        if (jump != null && rows.any { it.number == jump }) {
            selected = jump
        } else if (rows.none { it.number == selected }) {
            selected = rows.firstOrNull()?.number ?: 0
        }
    }

    /** Indeks baris tersorot pada [rows]; -1 bila tidak ada. */
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
     * Indeks (pada daftar asli) yang dipilih lewat Enter: nomor yang diketik; atau satu-satunya baris yang tampil.
     * Null bila tidak ada yang pasti (banyak baris tersaring, atau nomor di luar daftar).
     */
    fun pick(): Int? {
        val number = when {
            isNumber(query) -> query.toIntOrNull()?.takeIf { it in 1..total }
            rows.size == 1 -> rows[0].number
            else -> null
        }
        return number?.minus(1)
    }

    /** Penyaringan memakai [ChannelFilter] (satu-satunya aturan cocok); urutan dan instance dipertahankan. */
    private fun filtered(q: String): List<Row> {
        val kept = ChannelFilter.apply(all.map { it.channel }, q)
        val out = ArrayList<Row>(kept.size)
        var k = 0
        for (r in all) {
            if (k < kept.size && kept[k] === r.channel) {
                out += r
                k++
            }
        }
        return out
    }

    private fun isNumber(s: String) = s.isNotEmpty() && s.all { it in '0'..'9' }

    companion object {
        /** Lompatan tombol Prev/Next (CH+/CH- cepat di remote TV). */
        const val PAGE_SIZE = 10
    }
}
