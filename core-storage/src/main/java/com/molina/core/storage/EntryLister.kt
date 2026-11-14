// molina-patch26a
package com.molina.core.storage

import java.io.File
import java.nio.file.Files
import java.util.Locale

/**
 * Daftar isi folder dengan pencarian nama dan sortir (diadaptasi dari ExplorerList repo Molina-Studio).
 * Murni JVM dan blocking: panggil dari dispatcher IO.
 */
internal object EntryLister {
    const val MAX_RESULTS = ListOptions.MAX_RESULTS
    private const val MAX_SCANNED = 20_000
    private const val MAX_DEPTH = 12

    private fun isLink(f: File): Boolean = try {
        Files.isSymbolicLink(f.toPath())
    } catch (e: Exception) {
        false
    }

    private fun words(query: String): List<String> =
        query.trim().lowercase(Locale.ROOT).split(' ').filter { it.isNotEmpty() }

    private fun hit(name: String, w: List<String>): Boolean {
        if (w.isEmpty()) return true
        val n = name.lowercase(Locale.ROOT)
        return w.all { n.contains(it) }
    }

    fun matches(name: String, query: String): Boolean = hit(name, words(query))

    /** Null bila [dir] tidak bisa dibaca. [cancelled] true = berhenti dan kembalikan hasil sejauh ini. */
    fun list(dir: File, o: ListOptions, cancelled: () -> Boolean = { false }): ListResult? {
        val w = words(o.query)
        val top = dir.listFiles() ?: return null
        fun visible(f: File) = o.showHidden || !f.name.startsWith(".")
        fun extOk(f: File): Boolean {
            val only = o.onlyExt ?: return true
            return f.isDirectory || f.extension.lowercase(Locale.ROOT) in only
        }
        fun entry(f: File, rel: String?): FileEntry {
            val d = f.isDirectory
            return FileEntry(f.name, f.path, d, if (d) 0L else f.length(), f.lastModified(), rel)
        }
        val out = ArrayList<FileEntry>()
        if (!o.deep || w.isEmpty()) {
            for (f in top) {
                if (visible(f) && extOk(f) && hit(f.name, w)) out.add(entry(f, null))
            }
            return ListResult(sort(out, o), false)
        }
        var truncated = false
        var scanned = 0
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.add(Pair(dir, 0))
        loop@ while (queue.isNotEmpty()) {
            if (cancelled()) break
            val (d, depth) = queue.removeFirst()
            val kids = (if (d == dir) top else d.listFiles()) ?: continue
            for (f in kids) {
                scanned++
                if (scanned > MAX_SCANNED) {
                    truncated = true
                    break@loop
                }
                if (!visible(f)) continue
                if (f.isDirectory && !isLink(f) && depth < MAX_DEPTH) queue.add(Pair(f, depth + 1))
                if (extOk(f) && hit(f.name, w)) {
                    if (out.size >= MAX_RESULTS) {
                        truncated = true
                        break@loop
                    }
                    val rel = f.parentFile?.path.orEmpty().removePrefix(dir.path).trim('/')
                    out.add(entry(f, rel.ifEmpty { null }))
                }
            }
        }
        return ListResult(sort(out, o), truncated)
    }

    /** Folder di atas (opsional), lalu kunci sortir menaik/menurun. Folder diurut per nama bila kuncinya ukuran. */
    fun sort(items: List<FileEntry>, o: ListOptions): List<FileEntry> {
        fun lname(e: FileEntry) = e.name.lowercase(Locale.ROOT)
        fun cmp(k: SortKey): Comparator<FileEntry> = when (k) {
            SortKey.NAME -> compareBy<FileEntry> { lname(it) }
            SortKey.DATE -> compareBy<FileEntry> { it.lastModifiedMs }.thenBy { lname(it) }
            SortKey.SIZE -> compareBy<FileEntry> { it.sizeBytes }.thenBy { lname(it) }
            SortKey.TYPE -> compareBy<FileEntry> { it.extension.lowercase(Locale.ROOT) }.thenBy { lname(it) }
        }
        fun ordered(l: List<FileEntry>, k: SortKey): List<FileEntry> {
            val s = l.sortedWith(cmp(k))
            return if (o.ascending) s else s.reversed()
        }
        if (!o.foldersFirst) return ordered(items, o.sortKey)
        val dirKey = if (o.sortKey == SortKey.SIZE) SortKey.NAME else o.sortKey
        return ordered(items.filter { it.isDirectory }, dirKey) + ordered(items.filter { !it.isDirectory }, o.sortKey)
    }
}
