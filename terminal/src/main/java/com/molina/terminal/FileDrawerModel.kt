// molina-patch19
package com.molina.terminal

import java.io.File
import java.nio.file.Files

/** Keadaan tree drawer berkas; dipegang TerminalSessionHolder agar folder utama dan folder terbuka bertahan. Hanya dimutasi di main thread. */
internal class FileDrawerState {
    var root: File? = null
    val children = HashMap<String, List<DrawerEntry>>()
    val expanded = LinkedHashSet<String>()
}

/** Satu isi folder. [isDir] dihitung di thread IO supaya main thread tidak menyentuh disk. */
internal data class DrawerEntry(val file: File, val isDir: Boolean) {
    val name: String get() = file.name
    val hidden: Boolean get() = file.name.startsWith(".")
}

internal data class DrawerRow(val entry: DrawerEntry, val depth: Int, val expanded: Boolean)

/** Logika murni drawer (tanpa Android) agar mudah diuji JVM. */
internal object DrawerFs {
    const val INTERNAL_ROOT = "/storage/emulated/0"
    const val DEFAULT_ROOT = "/storage/emulated/0/Molina"
    private val PROTECTED = setOf("/storage/emulated/0/Molina", "/storage/emulated/0/Molina_Studio")

    /** BLOCKING: panggil dari Dispatchers.IO. Folder dulu, lalu berkas, urut nama. */
    fun list(dir: File): List<DrawerEntry>? {
        val kids = dir.listFiles() ?: return null
        return kids.map { DrawerEntry(it, it.isDirectory) }
            .sortedWith(compareByDescending<DrawerEntry> { it.isDir }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun flatten(state: FileDrawerState, root: File): List<DrawerRow> {
        val out = ArrayList<DrawerRow>()
        fun walk(dir: File, depth: Int) {
            state.children[dir.path]?.forEach { e ->
                val open = e.isDir && e.file.path in state.expanded
                out.add(DrawerRow(e, depth, open))
                if (open) walk(e.file, depth + 1)
            }
        }
        walk(root, 0)
        return out
    }

    /** BLOCKING. Penyimpanan internal + kartu SD. */
    fun volumes(): List<File> {
        val out = mutableListOf(File(INTERNAL_ROOT))
        File("/storage").listFiles()?.forEach {
            if (it.isDirectory && it.canRead() && it.name != "emulated" && it.name != "self") out.add(it)
        }
        return out
    }

    fun volumeLabel(v: File): String = if (v.path == INTERNAL_ROOT) "Internal" else v.name

    /** Label path untuk header: penyimpanan internal ditulis "Internal". */
    fun displayPath(path: String): String = when {
        path == INTERNAL_ROOT -> "Internal"
        path.startsWith("$INTERNAL_ROOT/") -> "Internal" + path.substring(INTERNAL_ROOT.length)
        else -> path
    }

    /** Path untuk shell sesi: proot melihat /storage/emulated/0 sebagai /sdcard. Null bila tidak terlihat di sesi itu. */
    fun shellPath(file: File, proot: Boolean): String? {
        val p = file.path
        if (!proot) return p
        return when {
            p == INTERNAL_ROOT -> "/sdcard"
            p.startsWith("$INTERNAL_ROOT/") -> "/sdcard" + p.substring(INTERNAL_ROOT.length)
            else -> null
        }
    }

    fun hasControlChars(s: String): Boolean = s.any { it.code < 32 || it.code == 127 }

    fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    fun validName(n: String): Boolean =
        n.isNotBlank() && n != "." && n != ".." && !n.contains('/') && !n.contains('\u0000')

    fun isProtected(f: File, vols: List<File>): Boolean =
        f.path in PROTECTED || vols.any { it.path == f.path }

    /** BLOCKING. Symlink hanya dilepas (tidak diikuti) supaya isi target tidak ikut terhapus. */
    fun deleteSafe(f: File): Boolean {
        if (Files.isSymbolicLink(f.toPath())) return f.delete()
        if (f.isDirectory) {
            f.listFiles()?.forEach { if (!deleteSafe(it)) return false }
        }
        return f.delete()
    }
}
