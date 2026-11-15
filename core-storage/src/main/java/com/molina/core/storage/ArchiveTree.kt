// molina-patch26k5a
package com.molina.core.storage

import java.util.Locale

/**
 * Simpul pohon isi arsip. [path] = kunci yang dipakai parameter `only` pada ekstrak: berkas "a/b.txt", folder "a/b/"
 * (berakhiran "/"). [size] = ukuran berkas (0 bila tidak diketahui). Untuk folder, [files], [folders], dan [bytes]
 * adalah jumlah berkas, jumlah subfolder, dan total ukuran seluruh isinya (rekursif); untuk berkas [bytes] = [size].
 */
data class TreeNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val files: Int,
    val folders: Int,
    val bytes: Long,
)

/** Satu ruas breadcrumb. Ruas akar punya [name] kosong dan [path] kosong; UI mengisinya dengan nama arsip. */
data class TreeCrumb(val name: String, val path: String)

/** Hasil pencarian nama. [capped] true bila masih ada hasil di luar batas yang diminta. */
data class TreeSearchResult(val hits: List<TreeNode>, val capped: Boolean)

enum class TreeSort { NAME, SIZE }

/**
 * Pohon folder virtual dari daftar entri datar sebuah arsip (logika murni, tanpa I/O).
 * - Folder implisit disintesis dari jalur berkas (arsip sering tidak menyimpan entri folder).
 * - Nama dinormalkan lewat [ArchiveNames.cleanEntry]; entri tidak aman (".." , NUL) dilewati dan dihitung di [skipped].
 * - Berkas bernama sama dalam satu arsip (tar bisa begitu): yang terakhir menang, jumlahnya di [duplicates].
 * - Berkas "a" dan folder "a/" boleh ada bersamaan (kunci berbeda); folder diurutkan lebih dulu.
 * - Daftar yang dipotong ([ArchiveListing.truncated]) menghasilkan pohon yang tidak lengkap; UI wajib memberi tahu.
 */
class ArchiveTree private constructor(
    private val kids: Map<String, List<TreeNode>>,
    private val folderKeys: Set<String>,
    private val all: List<TreeNode>,
    val totalFiles: Int,
    val totalFolders: Int,
    val totalBytes: Long,
    val skipped: Int,
    val duplicates: Int,
) {
    /** true untuk akar ("") dan folder yang ada. [path] folder berakhiran "/". */
    fun isFolder(path: String): Boolean = path.isEmpty() || path in folderKeys

    /** Anak langsung folder [folder] ("" = akar): folder dulu, lalu berkas menurut [sort]. */
    fun children(folder: String = "", sort: TreeSort = TreeSort.NAME): List<TreeNode> {
        val list = kids[folder] ?: return emptyList()
        return list.sortedWith(comparator(sort))
    }

    /** Cari nama (bukan jalur penuh), tanpa membedakan huruf besar/kecil, di seluruh pohon. */
    fun search(query: String, limit: Int): TreeSearchResult {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty() || limit <= 0) return TreeSearchResult(emptyList(), false)
        val hits = all.filter { it.name.lowercase(Locale.ROOT).contains(q) }
            .sortedWith(compareBy<TreeNode>({ !it.isDirectory }, { it.path.lowercase(Locale.ROOT) }, { it.path }))
        return TreeSearchResult(hits.take(limit), hits.size > limit)
    }

    /** Breadcrumb dari akar sampai [folder] (inklusif). */
    fun breadcrumbs(folder: String): List<TreeCrumb> {
        val out = ArrayList<TreeCrumb>()
        out.add(TreeCrumb("", ""))
        for (key in ancestorsOf(folder.trimEnd('/')) + (if (folder.isEmpty()) emptyList() else listOf(folder))) {
            out.add(TreeCrumb(lastName(key.trimEnd('/')), key))
        }
        return out
    }

    companion object {
        private fun comparator(sort: TreeSort): Comparator<TreeNode> = when (sort) {
            TreeSort.NAME -> compareBy<TreeNode>({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }, { it.name })
            TreeSort.SIZE -> compareBy<TreeNode>(
                { !it.isDirectory },
                { if (it.isDirectory) 0L else -it.size },
                { it.name.lowercase(Locale.ROOT) },
                { it.name },
            )
        }

        private fun lastName(clean: String): String = clean.substringAfterLast('/')

        /** Kunci folder tiap leluhur sejati dari jalur bersih [clean] (tanpa "/" di ujung): "a/b/c" -> ["a/", "a/b/"]. */
        private fun ancestorsOf(clean: String): List<String> {
            val out = ArrayList<String>()
            var i = clean.indexOf('/')
            while (i >= 0) {
                out.add(clean.substring(0, i + 1))
                i = clean.indexOf('/', i + 1)
            }
            return out
        }

        /** Kunci folder induk dari [path] (berkas atau folder); "" bila di akar. */
        fun parent(path: String): String {
            val t = path.trimEnd('/')
            val i = t.lastIndexOf('/')
            return if (i < 0) "" else t.substring(0, i + 1)
        }

        fun build(entries: List<ArchiveEntryInfo>): ArchiveTree {
            val fileSizes = LinkedHashMap<String, Long>()
            val dirKeys = LinkedHashSet<String>()
            var skipped = 0
            var duplicates = 0
            for (e in entries) {
                val clean = ArchiveNames.cleanEntry(e.name)
                if (clean == null) {
                    skipped++
                    continue
                }
                if (clean.isEmpty()) continue
                if (e.isDirectory) {
                    dirKeys.add("$clean/")
                } else if (fileSizes.put(clean, e.size.coerceAtLeast(0L)) != null) {
                    duplicates++
                }
                dirKeys.addAll(ancestorsOf(clean))
            }

            val dirFiles = HashMap<String, Int>()
            val dirBytes = HashMap<String, Long>()
            val dirFolders = HashMap<String, Int>()
            var totalFiles = 0
            var totalBytes = 0L
            for ((key, size) in fileSizes) {
                totalFiles++
                totalBytes += size
                for (anc in ancestorsOf(key)) {
                    dirFiles[anc] = (dirFiles[anc] ?: 0) + 1
                    dirBytes[anc] = (dirBytes[anc] ?: 0L) + size
                }
            }
            for (d in dirKeys) {
                for (anc in ancestorsOf(d.trimEnd('/'))) dirFolders[anc] = (dirFolders[anc] ?: 0) + 1
            }

            val kids = HashMap<String, ArrayList<TreeNode>>()
            val all = ArrayList<TreeNode>(fileSizes.size + dirKeys.size)
            for (d in dirKeys) {
                val node = TreeNode(
                    lastName(d.trimEnd('/')), d, true, 0L,
                    dirFiles[d] ?: 0, dirFolders[d] ?: 0, dirBytes[d] ?: 0L,
                )
                all.add(node)
                kids.getOrPut(parent(d)) { ArrayList() }.add(node)
            }
            for ((f, size) in fileSizes) {
                val node = TreeNode(lastName(f), f, false, size, 0, 0, size)
                all.add(node)
                kids.getOrPut(parent(f)) { ArrayList() }.add(node)
            }
            return ArchiveTree(kids, HashSet(dirKeys), all, totalFiles, dirKeys.size, totalBytes, skipped, duplicates)
        }
    }
}
