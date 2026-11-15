// molina-patch26k5b
package com.molina.core.storage

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

/** Hasil [ArchiveCache.open]: [file] ada di dalam slot cache; [cached] true bila tidak perlu diekstrak ulang. */
internal class CachedEntry(val file: File, val bytes: Long, val cached: Boolean)

/**
 * Cache entri arsip yang dibuka satu per satu (JVM murni, blocking). Tiap entri menempati satu "slot" (folder
 * bernama hash arsip+entri) berisi tepat satu berkas bernama dasar entri. Ekstrak berjalan di folder sementara
 * ".tmp-*" lalu di-rename atomik ke slot, jadi gagal atau batal tidak pernah meninggalkan slot setengah jadi.
 * Pagar keamanan sama dengan ekstrak biasa (ArchiveEngine.extract/Sink): nama bersih, jalur kanonik, batas byte,
 * ruang kosong, rollback. Slot dipangkas LRU (menurut waktu ubah slot, disentuh saat dipakai ulang) sampai
 * total <= [maxTotalBytes]. Kata sandi tidak masuk kunci, nama folder, atau log. Satu operasi sekaligus.
 */
internal class ArchiveCache(
    val root: File,
    val maxEntryBytes: Long = ArchiveEngineLimits.ENTRY_MAX_BYTES,
    val maxTotalBytes: Long = ArchiveEngineLimits.CACHE_MAX_BYTES,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private var seq = 0L

    /**
     * Buka [entryName] (nama entri di arsip, mis. dari ArchiveTree) ke cache. null = dibatalkan.
     * Gagal: ArchiveException (UNSAFE_ENTRY, NO_MATCH, TOO_LARGE, NEED_PASSWORD, WRONG_PASSWORD, NO_SPACE, ...).
     */
    fun open(archive: File, entryName: String, password: String?, ctl: ArchiveCtl): CachedEntry? {
        val clean = ArchiveNames.cleanEntry(entryName)
            ?: throw ArchiveException(ArchiveFailure.UNSAFE_ENTRY, "Entri arsip tidak aman")
        if (clean.isEmpty() || entryName.replace('\\', '/').endsWith("/")) {
            throw ArchiveException(ArchiveFailure.NO_MATCH, "Entri yang diminta bukan berkas")
        }
        if (isLink(root)) throw ArchiveException(ArchiveFailure.IO, "Folder cache tidak valid")
        if (!root.isDirectory && !root.mkdirs() && !root.isDirectory) {
            throw ArchiveException(ArchiveFailure.IO, "Gagal membuat folder cache")
        }
        val slot = File(root, keyOf(archive, clean))
        val hit = hitFile(slot)
        if (hit != null) {
            slot.setLastModified(clock())
            return CachedEntry(hit, hit.length(), true)
        }

        var tmp: File
        do {
            tmp = File(root, TMP_PREFIX + java.lang.Long.toString(clock(), 36) + "-" + seq)
            seq++
        } while (NameRules.present(tmp))
        if (!tmp.mkdirs()) throw ArchiveException(ArchiveFailure.IO, "Gagal membuat folder sementara")

        val out = try {
            ArchiveEngine.extract(
                archive, tmp, ExtractTarget.HERE, password, setOf(clean), ctl,
                flatten = true, policy = ConflictPolicy.RENAME, maxBytes = maxEntryBytes,
            )
        } catch (e: Throwable) {
            purge(tmp)
            throw e
        }
        if (out.cancelled) {
            purge(tmp)
            return null
        }
        val last = out.lastFile
        if (last == null) {
            purge(tmp)
            throw ArchiveException(ArchiveFailure.NO_MATCH, "Entri tidak ditemukan di arsip")
        }
        // Entri kembar (tar): hanya yang terakhir ditulis yang dipertahankan (sama dengan "yang terakhir menang" di ArchiveTree).
        for (f in tmp.listFiles().orEmpty()) if (f.name != last.name) purge(f)
        val produced = File(tmp, last.name)
        if (!produced.isFile) {
            purge(tmp)
            throw ArchiveException(ArchiveFailure.NO_MATCH, "Entri tidak ditemukan di arsip")
        }
        val size = produced.length()
        if (NameRules.present(slot)) purge(slot)
        if (!tmp.renameTo(slot)) {
            purge(tmp)
            throw ArchiveException(ArchiveFailure.IO, "Gagal menyimpan ke cache")
        }
        slot.setLastModified(clock())
        trim(slot)
        return CachedEntry(File(slot, last.name), size, false)
    }

    /** Pangkas slot tertua (LRU) sampai total <= [maxTotalBytes]. [keep] tidak pernah dihapus. Mengembalikan byte yang dibebaskan. */
    fun trim(keep: File? = null): Long {
        val slots = root.listFiles().orEmpty().filter { it.isDirectory && !isLink(it) && !it.name.startsWith(TMP_PREFIX) }
        var total = slots.sumOf { sizeOf(it) }
        var freed = 0L
        for (v in slots.filter { keep == null || it.name != keep.name }.sortedBy { it.lastModified() }) {
            if (total <= maxTotalBytes) break
            val s = sizeOf(v)
            purge(v)
            total -= s
            freed += s
        }
        return freed
    }

    /** Total byte isi cache (slot dan sisa ".tmp-*"); 0 bila folder cache belum ada. 26k5d:e31 */
    fun size(): Long {
        if (isLink(root) || !root.isDirectory) return 0L
        return root.listFiles().orEmpty().sumOf { sizeOf(it) }
    }

    /** Kosongkan seluruh cache (termasuk sisa ".tmp-*"). Mengembalikan byte yang dibebaskan. */
    fun clear(): Long {
        if (isLink(root) || !root.isDirectory) return 0L
        var freed = 0L
        for (f in root.listFiles().orEmpty()) {
            freed += sizeOf(f)
            purge(f)
        }
        return freed
    }

    private fun hitFile(slot: File): File? {
        if (isLink(slot) || !slot.isDirectory) return null
        val files = slot.listFiles().orEmpty().filter { it.isFile && !isLink(it) }
        return files.singleOrNull()
    }

    /** Kunci = hash jalur arsip + ukuran + waktu ubah + nama entri: arsip yang berubah otomatis menjadi cache baru. */
    private fun keyOf(archive: File, clean: String): String {
        val raw = archive.canonicalPath + "\u0000" + archive.length() + "\u0000" + archive.lastModified() + "\u0000" + clean
        val d = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(d.size * 2)
        for (b in d) sb.append(HEX[(b.toInt() shr 4) and 0xF]).append(HEX[b.toInt() and 0xF])
        return sb.toString()
    }

    private fun isLink(f: File): Boolean = try {
        Files.isSymbolicLink(f.toPath())
    } catch (e: Exception) {
        false
    }

    private fun sizeOf(f: File): Long {
        if (isLink(f)) return 0L
        if (f.isFile) return f.length()
        var n = 0L
        for (c in f.listFiles().orEmpty()) n += sizeOf(c)
        return n
    }

    /** Hapus rekursif tanpa mengikuti tautan simbolik. */
    private fun purge(f: File) {
        if (!isLink(f) && f.isDirectory) {
            for (c in f.listFiles().orEmpty()) purge(c)
        }
        f.delete()
    }

    private companion object {
        const val TMP_PREFIX = ".tmp-"
        val HEX = "0123456789abcdef".toCharArray()
    }
}
