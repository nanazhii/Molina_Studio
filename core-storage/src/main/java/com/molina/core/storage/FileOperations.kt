// molina-patch26a
package com.molina.core.storage

import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaError
import com.molina.core.common.MolinaResult
import com.molina.core.common.molinaCatching
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Operasi berkas dasar (diadaptasi dari FileOps repo Molina-Studio). Murni JVM dan blocking: panggil
 * dari dispatcher IO. Jalur di [protected] (dan induknya) tidak bisa dihapus atau diganti namanya.
 * Pesan galat tanpa nama/path; kelas exception asli tetap dilampirkan sebagai cause.
 */
internal object FileOperations {

    private fun fail(kind: ErrorKind, msg: String): MolinaResult.Failure =
        MolinaResult.Failure(MolinaError(kind, msg))

    private fun isLink(f: File): Boolean = try {
        Files.isSymbolicLink(f.toPath())
    } catch (e: Exception) {
        false
    }

    private fun entryOf(f: File): FileEntry =
        FileEntry(f.name, f.path, f.isDirectory, if (f.isDirectory) 0L else f.length(), f.lastModified())

    private fun isProtected(target: File, protectedPaths: Set<File>): Boolean {
        val t = target.canonicalFile.path
        return protectedPaths.any {
            val p = it.canonicalFile.path
            p == t || p.startsWith(t + File.separator)
        }
    }

    fun createFolder(parent: File, rawName: String): MolinaResult<FileEntry> =
        create(parent, rawName, folder = true)

    fun createFile(parent: File, rawName: String): MolinaResult<FileEntry> =
        create(parent, rawName, folder = false)

    private fun create(parent: File, rawName: String, folder: Boolean): MolinaResult<FileEntry> {
        val name = when (val v = NameRules.validate(rawName)) {
            is MolinaResult.Failure -> return v
            is MolinaResult.Success -> v.value
        }
        if (!parent.isDirectory) return fail(ErrorKind.NOT_FOUND, "Folder tidak ditemukan")
        if (!parent.canWrite()) return fail(ErrorKind.PERMISSION_DENIED, "Folder tidak bisa ditulisi")
        val target = File(parent, name)
        if (NameRules.present(target)) return fail(ErrorKind.ALREADY_EXISTS, "Nama sudah dipakai")
        return molinaCatching {
            val ok = if (folder) target.mkdir() else target.createNewFile()
            if (!ok) throw IOException(if (folder) "gagal membuat folder" else "gagal membuat berkas")
            entryOf(target)
        }
    }

    fun rename(src: File, rawName: String, protectedPaths: Set<File>): MolinaResult<FileEntry> {
        val name = when (val v = NameRules.validate(rawName)) {
            is MolinaResult.Failure -> return v
            is MolinaResult.Success -> v.value
        }
        if (!NameRules.present(src)) return fail(ErrorKind.NOT_FOUND, "Tidak ditemukan")
        if (isProtected(src, protectedPaths)) return fail(ErrorKind.PROTECTED, "Folder ini dilindungi")
        if (name == src.name) return MolinaResult.Success(entryOf(src))
        val parent = src.parentFile ?: return fail(ErrorKind.NOT_FOUND, "Folder induk tidak ditemukan")
        val target = File(parent, name)
        // Penyimpanan bersama tidak peka huruf besar/kecil: ganti kapitalisasi nama sendiri tetap diizinkan.
        if (!name.equals(src.name, ignoreCase = true) && NameRules.present(target)) {
            return fail(ErrorKind.ALREADY_EXISTS, "Nama sudah dipakai")
        }
        return molinaCatching {
            if (!src.renameTo(target)) throw IOException("gagal mengubah nama")
            entryOf(target)
        }
    }

    /** Hapus rekursif; tautan simbolik dihapus sebagai tautan (tidak diikuti). */
    fun delete(target: File, protectedPaths: Set<File>, cancelled: () -> Boolean = { false }): MolinaResult<Unit> {
        if (!NameRules.present(target)) return fail(ErrorKind.NOT_FOUND, "Tidak ditemukan")
        if (isProtected(target, protectedPaths)) return fail(ErrorKind.PROTECTED, "Folder ini dilindungi")
        return molinaCatching {
            if (!deleteRec(target, cancelled)) throw IOException("gagal menghapus")
        }
    }

    private fun deleteRec(f: File, cancelled: () -> Boolean): Boolean {
        if (cancelled()) throw IOException("dibatalkan")
        if (!isLink(f) && f.isDirectory) {
            val kids = f.listFiles()
            if (kids != null) for (k in kids) if (!deleteRec(k, cancelled)) return false
        }
        return f.delete()
    }
}
