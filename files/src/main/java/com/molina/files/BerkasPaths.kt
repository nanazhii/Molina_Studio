// molina-27d
package com.molina.files

import com.molina.core.common.MolinaPaths
import java.io.File

/** Logika murni tab Berkas (tanpa Android) agar bisa diuji JVM. */
internal object BerkasPaths {
    /** Path relatif [f] terhadap [root] (pemisah '/'); null bila di luar root, root itu sendiri, atau di dalam Sampah. */
    fun relative(root: File, f: File): String? {
        val base = runCatching { root.canonicalPath }.getOrNull() ?: return null
        val p = runCatching { f.canonicalPath }.getOrNull() ?: return null
        if (p == base || !p.startsWith(base + File.separator)) return null
        val rel = p.substring(base.length + 1).replace(File.separatorChar, '/')
        if (rel == MolinaPaths.TRASH_DIR || rel.startsWith(MolinaPaths.TRASH_DIR + "/")) return null
        return rel
    }

    /** [molina-27e] Path relatif folder tujuan: "" untuk root itu sendiri; null di luar root atau di dalam Sampah. */
    fun destRelative(root: File, dir: File): String? {
        val base = runCatching { root.canonicalPath }.getOrNull() ?: return null
        val p = runCatching { dir.canonicalPath }.getOrNull() ?: return null
        if (p == base) return ""
        return relative(root, dir)
    }
}
