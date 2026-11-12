package com.molina.core.storage

import com.molina.core.common.DefaultDispatcherProvider
import com.molina.core.common.DispatcherProvider
import com.molina.core.common.MolinaPaths
import com.molina.core.common.MolinaResult
import com.molina.core.common.molinaCatching
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** Akses berkas di bawah root Molina. UI hanya bergantung pada antarmuka ini. */
interface MolinaStorage {
    /** Membuat root dan subfolder standar bila belum ada. Aman dipanggil berulang. */
    suspend fun ensureLayout(): MolinaResult<Unit>

    /** Mendaftar isi folder [relative] (relatif terhadap root). Folder lebih dulu, lalu nama. */
    suspend fun list(relative: String = ""): MolinaResult<List<FileEntry>>
}

class LocalMolinaStorage(
    private val paths: MolinaPaths = MolinaPaths(),
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) : MolinaStorage {

    override suspend fun ensureLayout(): MolinaResult<Unit> = withContext(dispatchers.io) {
        molinaCatching {
            for (dir in paths.layout) {
                if (dir.isDirectory) continue
                if (dir.exists()) throw IOException("bukan folder")
                if (!dir.mkdirs() && !dir.isDirectory) throw IOException("gagal membuat folder")
            }
        }
    }

    override suspend fun list(relative: String): MolinaResult<List<FileEntry>> =
        withContext(dispatchers.io) {
            when (val resolved = PathGuard.resolveInside(paths.root, relative)) {
                is MolinaResult.Failure -> resolved
                is MolinaResult.Success -> listDirectory(resolved.value)
            }
        }

    private fun listDirectory(dir: File): MolinaResult<List<FileEntry>> = molinaCatching {
        if (!dir.exists()) throw FileNotFoundException()
        if (!dir.isDirectory) throw IOException("bukan folder")
        val children = dir.listFiles() ?: throw SecurityException()
        children
            .map { FileEntry(it.name, it.path, it.isDirectory, if (it.isDirectory) 0L else it.length(), it.lastModified()) }
            .sortedWith(compareByDescending<FileEntry> { it.isDirectory }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
}
