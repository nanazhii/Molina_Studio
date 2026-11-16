// molina-27x
package com.molina.files

import com.molina.core.storage.TreeNode
import com.molina.core.ui.ExtLabel

/** [molina-27x] Teks baris kedua daftar isi arsip. Murni JVM. */
internal object ArchiveRowText {
    private const val DOT = " \u00B7 "

    fun second(n: TreeNode, searching: Boolean): String {
        if (searching) return ArchiveViewText.hitLine(n)
        if (n.isDirectory) {
            return if (n.files == 0 && n.folders == 0) "Folder kosong" else ArchiveViewText.nodeLine(n)
        }
        val ext = ExtLabel.of(n.name)
        return FileSizeFormat.format(n.size) + if (ext.isEmpty()) "" else DOT + ext
    }
}
