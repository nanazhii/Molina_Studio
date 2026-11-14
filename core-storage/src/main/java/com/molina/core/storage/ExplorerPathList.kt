// molina-patch26e1
package com.molina.core.storage

/**
 * Daftar path relatif terhadap root Molina, disimpan satu path per baris. Root ("") ditulis "/".
 * Path berisi "." / ".." / NUL / baris baru ditolak. Murni JVM.
 */
internal object ExplorerPathList {
    /** Path rapi tanpa garis miring di ujung; null bila tidak valid. */
    fun normalize(path: String): String? {
        if (path.contains('\u0000') || path.contains('\n') || path.contains('\r')) return null
        val parts = path.trim().split('/').filter { it.isNotEmpty() }
        if (parts.any { it == "." || it == ".." }) return null
        return parts.joinToString("/")
    }

    fun decode(raw: String?): List<String> =
        raw.orEmpty().split('\n').mapNotNull { line ->
            val t = line.trim()
            if (t.isEmpty()) null else normalize(t)
        }.distinct()

    fun encode(list: List<String>): String = list.joinToString("\n") { if (it.isEmpty()) "/" else it }
}
