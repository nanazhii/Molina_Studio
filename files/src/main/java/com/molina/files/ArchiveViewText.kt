// molina-patch26k5c
package com.molina.files

import com.molina.core.storage.ArchiveTree
import com.molina.core.storage.TreeNode

/** Teks layar "Lihat isi" arsip. Murni JVM. */
internal object ArchiveViewText {
    private const val DOT = " \u00B7 "

    /** 50000 -> "50.000" (deterministik, tidak bergantung locale). */
    fun grouped(n: Int): String = n.toString().reversed().chunked(3).joinToString(".").reversed()

    fun nodeLine(n: TreeNode): String {
        if (!n.isDirectory) return FileSizeFormat.format(n.size)
        val parts = ArrayList<String>()
        if (n.folders > 0) parts.add("${n.folders} folder")
        parts.add("${n.files} berkas")
        parts.add(FileSizeFormat.format(n.bytes))
        return parts.joinToString(DOT)
    }

    /** Baris kedua hasil pencarian: folder induk di dalam arsip + ukuran/isi. */
    fun hitLine(n: TreeNode): String {
        val parent = ArchiveTree.parent(n.path)
        val where = if (parent.isEmpty()) "/" else "/$parent"
        return where + DOT + nodeLine(n)
    }

    fun truncatedBanner(shown: Int): String =
        "Daftar dipotong: hanya ${grouped(shown)} entri pertama dimuat. Entri lain tidak ditampilkan dan tidak ikut pencarian. Ketuk untuk menutup."

    fun searchCapped(limit: Int): String = "Menampilkan $limit hasil pertama. Persempit kata kunci."

    fun emptyFolder(): String = "Folder ini kosong"

    fun noMatch(): String = "Tidak ada nama yang cocok"

    fun selectionLine(count: Int): String = "$count dipilih"

    fun counts(files: Int, folders: Int, bytes: Long): String =
        "${grouped(files)} berkas" + (if (folders > 0) DOT + "${grouped(folders)} folder" else "") + DOT + FileSizeFormat.format(bytes)

    fun cacheCleared(bytes: Long): String =
        if (bytes <= 0L) "Cache sudah kosong" else "Cache dibersihkan (${FileSizeFormat.format(bytes)})"

    fun opening(): String = "Membuka\u2026 ketuk untuk batal"

    /** Label menu "Ekstrak ke..." di lembar: jelas apa yang akan diekstrak. 26k5f:e02 */
    fun extractToLabel(selecting: Boolean): String =
        if (selecting) "Ekstrak terpilih ke\u2026" else "Ekstrak semua ke\u2026"

    /** Baris kuota cache: "Cache arsip 120 / 300 MB" (<1 untuk isi di bawah 1 MB). 26k5d:e01 */
    fun cacheLine(used: Long, max: Long): String {
        val mb = 1024L * 1024L
        val u = if (used <= 0L) "0" else if (used < mb) "<1" else (used / mb).toString()
        return "Cache arsip $u / ${max / mb} MB"
    }

    fun tooLargeAsk(): String = "Berkas ini terlalu besar untuk dibuka dari cache.\n\nEkstrak berkas ini ke folder arsip?"
}
