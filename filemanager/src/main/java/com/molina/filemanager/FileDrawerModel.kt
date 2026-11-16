// molina-patch27b: disalin dari :terminal (FileDrawerModel.kt), sumber file manager sekarang di :filemanager
// molina-patch19
package com.molina.filemanager

import java.io.File
import java.nio.file.Files

/** Keadaan tree drawer berkas; dipegang TerminalSessionHolder agar folder utama dan folder terbuka bertahan. Hanya dimutasi di main thread. */
class FileDrawerState {
    var root: File? = null
    val children = HashMap<String, List<DrawerEntry>>()
    val expanded = LinkedHashSet<String>()
    var showHidden: Boolean = true // molina-27k: Berkas tertanam menyembunyikan file berawalan titik (seperti Android)
    var sort: DrawerSort = DrawerSort.NAME_ASC // molina-27k
}

/** Satu isi folder. [isDir] dihitung di thread IO supaya main thread tidak menyentuh disk. */
data class DrawerEntry(val file: File, val isDir: Boolean, val size: Long = 0L, val modified: Long = 0L) {
    val name: String get() = file.name
    val hidden: Boolean get() = file.name.startsWith(".")
}

data class DrawerRow(val entry: DrawerEntry, val depth: Int, val expanded: Boolean)

/** Logika murni drawer (tanpa Android) agar mudah diuji JVM. */
object DrawerFs {
    const val INTERNAL_ROOT = "/storage/emulated/0"
    const val DEFAULT_ROOT = "/storage/emulated/0/Molina"
    private val PROTECTED = setOf("/storage/emulated/0/Molina", "/storage/emulated/0/Molina_Studio")
    const val MOLINA_STUDIO = "/storage/emulated/0/Molina_Studio" // molina-27k

    /** [molina-27k] Folder rumah Molina (Molina, Molina_Studio): diberi aksen dan bintang di daftar. */
    fun isMolinaHome(path: String): Boolean = path == DEFAULT_ROOT || path == MOLINA_STUDIO

    /** BLOCKING: panggil dari Dispatchers.IO. Folder dulu, lalu berkas, urut nama. */
    fun list(dir: File): List<DrawerEntry>? {
        val kids = dir.listFiles() ?: return null
        return kids.filter { it.name != ".molina-trash" && it.name != ".arsip-cache" } // molina-27j: folder internal Molina disembunyikan
            .map { val d = it.isDirectory; DrawerEntry(it, d, if (d) 0L else it.length(), it.lastModified()) } // molina-27k
            .sortedWith(compareByDescending<DrawerEntry> { it.isDir }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun flatten(state: FileDrawerState, root: File): List<DrawerRow> {
        val out = ArrayList<DrawerRow>()
        val cmp = DrawerSort.comparator(state.sort) // molina-27k
        fun walk(dir: File, depth: Int) {
            state.children[dir.path]?.filter { state.showHidden || !it.hidden }?.sortedWith(cmp)?.forEach { e ->
                val open = e.isDir && e.file.path in state.expanded
                out.add(DrawerRow(e, depth, open))
                if (open) walk(e.file, depth + 1)
            }
        }
        walk(root, 0)
        return out
    }

    /**
     * [molina-27k] BLOCKING (IO). Cari nama yang memuat [query] (tanpa beda huruf) di bawah [root], melebar per tingkat
     * (hasil dangkal dulu). Symlink tidak diikuti; folder internal Molina disembunyikan; maksimum [limit] hasil.
     */
    fun search(
        root: File,
        query: String,
        showHidden: Boolean,
        limit: Int = 300,
        maxVisited: Int = 50000,
        cancelled: () -> Boolean = { false },
    ): List<File> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<File>()
        val queue = ArrayDeque<File>()
        queue.addLast(root)
        var visited = 0
        while (queue.isNotEmpty() && out.size < limit && visited < maxVisited && !cancelled()) {
            val kids = queue.removeFirst().listFiles() ?: continue
            for (k in kids.sortedBy { it.name.lowercase() }) {
                if (k.name == ".molina-trash" || k.name == ".arsip-cache") continue
                if (!showHidden && k.name.startsWith(".")) continue
                visited++
                if (k.name.lowercase().contains(q)) {
                    out.add(k)
                    if (out.size >= limit) break
                }
                if (k.isDirectory && !Files.isSymbolicLink(k.toPath())) queue.addLast(k)
            }
        }
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

    /** [molina-27g0] Rantai folder dari anak langsung [root] sampai [dir]; kosong bila sama; null bila [dir] di luar [root]. */
    fun chainTo(root: File, dir: File): List<File>? {
        if (dir.path == root.path) return emptyList()
        if (!dir.path.startsWith(root.path.trimEnd('/') + "/")) return null
        val out = ArrayList<File>()
        var c: File? = dir
        while (c != null && c.path != root.path) { out.add(0, c); c = c.parentFile }
        return out
    }

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
