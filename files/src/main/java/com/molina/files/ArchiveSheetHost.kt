// molina-27f
package com.molina.files

import com.molina.core.storage.ConflictPolicy
import com.molina.core.storage.ExtractTarget
import com.molina.core.storage.MolinaStorage
import java.io.File

/**
 * [molina-27f] Pemilik [ArchiveSheet] (Lihat isi arsip). Dulu hanya FilesFragment; kini BerkasFragment juga.
 * Publik karena Fragment publik tidak boleh memakai supertipe internal.
 */
interface ArchiveSheetHost {
    val archiveStorage: MolinaStorage
    fun archiveBusy(): Boolean
    fun archiveFile(relative: String): File

    /** "Ekstrak ke..." dari lembar: pasang klip arsip (dengan [only]); false bila pilihan terlalu besar. */
    fun setSheetArchiveClip(archive: String, only: Set<String>?): Boolean

    /** Buka berkas hasil cache lewat aplikasi lain. null = berhasil; selain itu teks galat. */
    fun viewArchiveEntry(relative: String): String?

    fun startSheetExtract(
        archive: String,
        only: Set<String>?,
        target: ExtractTarget,
        policy: ConflictPolicy,
        password: String?,
    ): Boolean
}

/** Pembungkus tanpa mengubah FilesFragment (jalan mundur tetap utuh): meneruskan ke anggota internal miliknya. */
internal class FilesFragmentArchiveHost(private val f: FilesFragment) : ArchiveSheetHost {
    override val archiveStorage: MolinaStorage get() = f.archiveStorage
    override fun archiveBusy(): Boolean = f.archiveBusy()
    override fun archiveFile(relative: String): File = f.archiveFile(relative)
    override fun setSheetArchiveClip(archive: String, only: Set<String>?): Boolean = f.setSheetArchiveClip(archive, only)
    override fun viewArchiveEntry(relative: String): String? = f.viewArchiveEntry(relative)
    override fun startSheetExtract(
        archive: String,
        only: Set<String>?,
        target: ExtractTarget,
        policy: ConflictPolicy,
        password: String?,
    ): Boolean = f.startSheetExtract(archive, only, target, policy, password)
}
