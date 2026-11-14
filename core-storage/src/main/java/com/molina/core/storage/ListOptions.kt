// molina-patch26a
package com.molina.core.storage

enum class SortKey { NAME, DATE, SIZE, TYPE }

/**
 * Opsi daftar isi folder. [query]: semua kata (dipisah spasi) harus ada di nama, tanpa membedakan
 * huruf besar/kecil. [deep] mencari sampai subfolder. [onlyExt]: ekstensi huruf kecil yang
 * ditampilkan untuk berkas (folder selalu tampil); null = semua.
 */
data class ListOptions(
    val sortKey: SortKey = SortKey.NAME,
    val ascending: Boolean = true,
    val foldersFirst: Boolean = true,
    val showHidden: Boolean = false,
    val query: String = "",
    val deep: Boolean = false,
    val onlyExt: Set<String>? = null,
) {
    companion object {
        /** Batas hasil pencarian sampai subfolder; lewat batas = [ListResult.truncated]. */
        const val MAX_RESULTS = 500
    }
}

/** [truncated] true bila pencarian dalam berhenti karena batas hasil/entri terpindai. */
data class ListResult(val entries: List<FileEntry>, val truncated: Boolean)
