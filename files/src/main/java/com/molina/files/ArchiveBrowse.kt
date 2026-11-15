// molina-patch26k5c
package com.molina.files

import com.molina.core.storage.ArchiveTree
import com.molina.core.storage.TreeNode
import com.molina.core.storage.TreeSort

/** Baris yang ditampilkan. [capped]: hasil pencarian dipotong di [ArchiveBrowse.SEARCH_LIMIT]. */
internal data class BrowseRows(val nodes: List<TreeNode>, val capped: Boolean, val searching: Boolean)

/**
 * Keadaan penelusuran "Lihat isi" arsip (logika murni, tanpa Android). Tidak memuat kata sandi.
 * Kunci pilihan = [TreeNode.path] (berkas "a/b.txt", folder "a/b/").
 */
internal data class ArchiveBrowse(
    val folder: String = "",
    val sort: TreeSort = TreeSort.NAME,
    val query: String = "",
    val selected: Set<String> = emptySet(),
) {
    val searching: Boolean get() = query.isNotBlank()
    val selecting: Boolean get() = selected.isNotEmpty()

    fun rows(tree: ArchiveTree): BrowseRows {
        if (searching) {
            val r = tree.search(query, SEARCH_LIMIT)
            return BrowseRows(r.hits, r.capped, true)
        }
        return BrowseRows(tree.children(folder, sort), false, false)
    }

    /** Masuk ke folder [node]; pencarian dan pilihan direset. Berkas: tidak berubah. */
    fun open(node: TreeNode): ArchiveBrowse =
        if (node.isDirectory) copy(folder = node.path, query = "", selected = emptySet()) else this

    fun goTo(path: String): ArchiveBrowse = copy(folder = path, query = "", selected = emptySet())

    fun toggle(path: String): ArchiveBrowse =
        copy(selected = if (path in selected) selected - path else selected + path)

    fun selectAll(tree: ArchiveTree): ArchiveBrowse = copy(selected = rows(tree).nodes.map { it.path }.toSet())

    fun clearSelection(): ArchiveBrowse = copy(selected = emptySet())

    fun withQuery(q: String): ArchiveBrowse = copy(query = q, selected = emptySet())

    fun withSort(s: TreeSort): ArchiveBrowse = copy(sort = s)

    /** Tombol Kembali: tutup pencarian, lalu pilihan, lalu naik satu folder. null = tidak ada lagi (tutup lembar). */
    fun back(): ArchiveBrowse? = when {
        searching -> copy(query = "")
        selecting -> copy(selected = emptySet())
        folder.isNotEmpty() -> copy(folder = ArchiveTree.parent(folder))
        else -> null
    }

    /** Kunci untuk parameter `only`; null = tidak ada pilihan (semua). Kunci di dalam folder yang juga dipilih dibuang. */
    fun onlyKeys(): Set<String>? {
        if (selected.isEmpty()) return null
        return selected.filter { k -> selected.none { f -> f != k && f.endsWith("/") && k.startsWith(f) } }.toSet()
    }

    companion object {
        const val SEARCH_LIMIT = 200

        /** Pilihan lebih besar dari ini tidak disimpan ke Bundle (batas ukuran transaksi). */
        const val MAX_SAVED_SELECTION = 500
    }
}
