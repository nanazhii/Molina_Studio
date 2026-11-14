// molina-patch26h
package com.molina.core.storage

import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaError
import com.molina.core.common.MolinaPaths
import com.molina.core.common.MolinaResult
import com.molina.core.common.molinaCatching
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Properties

/**
 * Satu item di Sampah. [origin] = path asal RELATIF terhadap root (bukan absolut), jadi Sampah milik satu
 * root tidak pernah salah tafsir bila root diganti. Item = folder `<id>/` berisi `info.properties` dan
 * `files/<nama asli>`.
 */
class TrashItem internal constructor(
    val id: String,
    val name: String,
    val origin: String,
    val deletedAt: Long,
    val isDirectory: Boolean,
    internal val dir: File,
) {
    internal val payload: File get() = File(File(dir, "files"), name)

    /** Folder asal relatif terhadap root; "" = root. */
    val originParent: String get() = origin.substringBeforeLast('/', "")

    /** Sisa hari sebelum dibersihkan otomatis (minimal 0). */
    fun daysLeft(now: Long, retainDays: Int = TrashBin.RETAIN_DAYS): Int {
        val used = ((now - deletedAt) / TrashBin.DAY_MS).coerceAtLeast(0L)
        return (retainDays - used).coerceAtLeast(0L).toInt()
    }
}

/**
 * Sampah Molina (port TrashBin repo lama, JVM murni). Berada di `<root>/.molina-trash`, jadi pemindahan
 * memakai rename pada volume yang sama: instan dan atomik. Semua fungsi blocking: panggil dari IO.
 */
class TrashBin(private val root: File) {
    private val trashDir: File get() = File(root, MolinaPaths.TRASH_DIR)

    private fun fail(kind: ErrorKind, msg: String): MolinaResult.Failure =
        MolinaResult.Failure(MolinaError(kind, msg))

    /**
     * Memindahkan [files] (sudah diselesaikan PathGuard) ke Sampah. Semua dicek dulu (ada, tidak
     * dilindungi, di dalam root); bila gagal di tengah, yang sudah dipindah dikembalikan. Item yang sudah
     * tercakup folder induk yang ikut dipilih, dan duplikat, dilewati.
     */
    fun trash(
        files: List<File>,
        protectedPaths: Set<File>,
        now: Long = System.currentTimeMillis(),
    ): MolinaResult<List<TrashItem>> {
        if (files.isEmpty()) return MolinaResult.Success(emptyList())
        val base = try {
            root.canonicalFile
        } catch (e: IOException) {
            return MolinaResult.Failure(MolinaError(ErrorKind.IO, "Kesalahan I/O", e))
        }
        val rels = ArrayList<String>(files.size)
        for (f in files) {
            if (!NameRules.present(f)) return fail(ErrorKind.NOT_FOUND, "Tidak ditemukan")
            if (isProtected(f, protectedPaths)) return fail(ErrorKind.PROTECTED, "Folder ini dilindungi")
            val rel = relativeTo(base, f) ?: return fail(ErrorKind.INVALID_PATH, "Path tidak valid")
            if (rel == MolinaPaths.TRASH_DIR || rel.startsWith(MolinaPaths.TRASH_DIR + "/")) {
                return fail(ErrorKind.PROTECTED, "Folder ini dilindungi")
            }
            rels += rel
        }
        val keep = files.indices.filter { i ->
            rels.indices.none { j ->
                j != i && (rels[i].startsWith(rels[j] + "/") || (rels[i] == rels[j] && j < i))
            }
        }
        return molinaCatching {
            val out = ArrayList<TrashItem>(keep.size)
            try {
                for (i in keep) out += trashOne(files[i], rels[i], now)
            } catch (e: Exception) {
                runCatching { restoreItems(out) }
                throw e
            }
            out
        }
    }

    /** Semua item yang sah, terbaru dulu. Folder rusak/yatim diabaikan (dibersihkan oleh [empty]). */
    fun list(): List<TrashItem> {
        val out = ArrayList<TrashItem>()
        trashDir.listFiles()?.forEach { d -> if (d.isDirectory) readItem(d)?.let { out += it } }
        out.sortByDescending { it.deletedAt }
        return out
    }

    /** Mengembalikan item ke lokasi asal (folder asal dibuat bila hilang; nama bentrok diberi " (1)"). */
    fun restore(ids: List<String>): MolinaResult<List<String>> {
        val wanted = ids.toSet()
        val found = list().filter { it.id in wanted }
        if (found.isEmpty()) return fail(ErrorKind.NOT_FOUND, "Tidak ditemukan di Sampah")
        return molinaCatching { restoreItems(found) }
    }

    /** Menghapus permanen item tertentu dari Sampah; hasilnya jumlah yang terhapus. */
    fun purge(ids: List<String>): MolinaResult<Int> {
        val wanted = ids.toSet()
        val found = list().filter { it.id in wanted }
        return molinaCatching { found.count { deleteTree(it.dir) } }
    }

    /** Mengosongkan seluruh Sampah (termasuk folder rusak/yatim); hasilnya jumlah entri yang terhapus. */
    fun empty(): MolinaResult<Int> = molinaCatching {
        var n = 0
        trashDir.listFiles()?.forEach { d -> if (d.name != NOMEDIA && deleteTree(d)) n++ }
        n
    }

    /** Membersihkan item yang lebih tua dari [days] hari. Jam mundur tidak menghapus apa pun. */
    fun purgeExpired(now: Long = System.currentTimeMillis(), days: Int = RETAIN_DAYS): MolinaResult<Int> {
        val old = list().filter { now - it.deletedAt > days * DAY_MS }
        return molinaCatching { old.count { deleteTree(it.dir) } }
    }

    // ---- internal

    private fun trashOne(f: File, rel: String, now: Long): TrashItem {
        val dir = trashDir
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) throw IOException("Sampah tidak bisa dibuat")
        runCatching { File(dir, NOMEDIA).createNewFile() }
        var n = 0
        var itemDir = File(dir, "$now-$n")
        while (!itemDir.mkdir()) {
            if (!itemDir.exists() || ++n > MAX_ID_TRIES) throw IOException("Sampah tidak bisa dibuat")
            itemDir = File(dir, "$now-$n")
        }
        val filesDir = File(itemDir, FILES)
        if (!filesDir.mkdir()) {
            itemDir.delete()
            throw IOException("Sampah tidak bisa dibuat")
        }
        val isDir = !isLink(f) && f.isDirectory
        val payload = File(filesDir, f.name)
        if (!f.renameTo(payload)) {
            filesDir.delete()
            itemDir.delete()
            throw IOException("gagal memindahkan ke Sampah")
        }
        val item = TrashItem(itemDir.name, f.name, rel, now, isDir, itemDir)
        try {
            writeInfo(item)
        } catch (e: Exception) {
            payload.renameTo(f)
            deleteTree(itemDir)
            throw IOException("gagal mencatat item Sampah")
        }
        return item
    }

    private fun restoreItems(items: List<TrashItem>): List<String> {
        val out = ArrayList<String>(items.size)
        for (item in items) {
            val parentRel = item.originParent
            val parent = when (val r = PathGuard.resolveInside(root, parentRel)) {
                is MolinaResult.Failure -> throw IOException("lokasi asal tidak valid")
                is MolinaResult.Success -> r.value
            }
            if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) {
                throw IOException("folder asal tidak bisa dibuat")
            }
            val target = uniqueIn(parent, item.name, keepExt = !item.isDirectory)
            if (!item.payload.renameTo(target)) throw IOException("gagal memulihkan")
            deleteTree(item.dir)
            out += if (parentRel.isEmpty()) target.name else parentRel + "/" + target.name
        }
        return out
    }

    private fun writeInfo(item: TrashItem) {
        val p = Properties()
        p.setProperty("v", "1")
        p.setProperty("name", item.name)
        p.setProperty("origin", item.origin)
        p.setProperty("at", item.deletedAt.toString())
        p.setProperty("dir", item.isDirectory.toString())
        File(item.dir, INFO).outputStream().use { o ->
            val w = OutputStreamWriter(o, StandardCharsets.UTF_8)
            p.store(w, null)
            w.flush()
        }
    }

    private fun readItem(itemDir: File): TrashItem? = try {
        val p = Properties()
        File(itemDir, INFO).inputStream().use { i -> p.load(InputStreamReader(i, StandardCharsets.UTF_8)) }
        val name = p.getProperty("name")
        val origin = p.getProperty("origin")
        val at = p.getProperty("at")?.toLongOrNull()
        if (name == null || origin == null || at == null || !validName(name) || !validOrigin(origin)) {
            null
        } else {
            val item = TrashItem(itemDir.name, name, origin, at, p.getProperty("dir") == "true", itemDir)
            if (present(item.payload)) item else null
        }
    } catch (e: Exception) {
        null
    }

    private fun validName(n: String): Boolean =
        n.isNotEmpty() && n != "." && n != ".." && !n.contains('/') && !n.contains('\u0000') && n.length <= 255

    private fun validOrigin(o: String): Boolean =
        o.isNotEmpty() && !o.startsWith("/") && !o.contains('\u0000') &&
            o.split('/').none { it.isEmpty() || it == "." || it == ".." }

    private fun isLink(f: File): Boolean = try {
        Files.isSymbolicLink(f.toPath())
    } catch (e: Exception) {
        false
    }

    private fun present(f: File): Boolean = f.exists() || isLink(f)

    private fun isProtected(target: File, protectedPaths: Set<File>): Boolean = try {
        val t = target.canonicalFile.path
        protectedPaths.any {
            val p = it.canonicalFile.path
            p == t || p.startsWith(t + File.separator)
        }
    } catch (e: IOException) {
        true
    }

    /** Path relatif [f] terhadap [base] tanpa mengikuti tautan pada [f] sendiri; null bila di luar atau sama dengan root. */
    private fun relativeTo(base: File, f: File): String? {
        val parent = f.absoluteFile.parentFile ?: return null
        val p = try {
            parent.canonicalPath
        } catch (e: IOException) {
            return null
        }
        val full = if (p.endsWith(File.separator)) p + f.name else p + File.separator + f.name
        val b = base.path
        if (full == b || !full.startsWith(b + File.separator)) return null
        return full.substring(b.length + 1).replace(File.separatorChar, '/')
    }

    private fun uniqueIn(dir: File, name: String, keepExt: Boolean): File {
        var f = File(dir, name)
        if (!present(f)) return f
        val dot = name.lastIndexOf('.')
        val base = if (keepExt && dot > 0) name.substring(0, dot) else name
        val ext = if (keepExt && dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            f = File(dir, "$base ($i)$ext")
            if (!present(f)) return f
            i++
        }
    }

    private fun deleteTree(f: File): Boolean {
        if (!isLink(f) && f.isDirectory) {
            f.listFiles()?.forEach { if (!deleteTree(it)) return false }
        }
        return f.delete()
    }

    companion object {
        const val RETAIN_DAYS = 30
        internal const val DAY_MS = 24L * 60 * 60 * 1000
        private const val INFO = "info.properties"
        private const val FILES = "files"
        private const val NOMEDIA = ".nomedia"
        private const val MAX_ID_TRIES = 10_000
    }
}
