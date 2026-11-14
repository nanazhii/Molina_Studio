package com.molina.core.storage

import com.molina.core.common.DefaultDispatcherProvider
import com.molina.core.common.DispatcherProvider
import com.molina.core.common.MolinaPaths
import com.molina.core.common.MolinaResult
import com.molina.core.common.molinaCatching
import kotlinx.coroutines.isActive
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

    // molina-patch26a
    /** Daftar isi [relative] dengan pencarian, sortir, dan filter sesuai [options]. */
    suspend fun browse(relative: String = "", options: ListOptions = ListOptions()): MolinaResult<ListResult>

    suspend fun createFolder(parentRelative: String, name: String): MolinaResult<FileEntry>

    suspend fun createFile(parentRelative: String, name: String): MolinaResult<FileEntry>

    /** Mengubah nama item di [relative] (hanya nama, bukan path). Root dan folder standar dilindungi. */
    suspend fun rename(relative: String, newName: String): MolinaResult<FileEntry>

    /** Menghapus item (rekursif). Root dan folder standar dilindungi. */
    suspend fun delete(relative: String): MolinaResult<Unit>

    // molina-patch26c
    /**
     * Menyalin [sources] ke folder [destRelative]. Nama bentrok diberi nomor, tidak menimpa. Folder tidak
     * bisa disalin ke dalam dirinya sendiri. Hasil sebagian tetap dikembalikan saat [cancel] dipicu.
     */
    suspend fun copy(
        sources: List<String>,
        destRelative: String,
        cancel: CancelToken = CancelToken(),
        onProgress: (TransferProgress) -> Unit = {},
    ): MolinaResult<BatchResult>

    /** Memindahkan [sources] ke [destRelative]. Root dan folder standar dilindungi. */
    suspend fun move(
        sources: List<String>,
        destRelative: String,
        cancel: CancelToken = CancelToken(),
        onProgress: (TransferProgress) -> Unit = {},
    ): MolinaResult<BatchResult>

    /** Menghapus banyak item sekaligus (rekursif). Root dan folder standar dilindungi. */
    suspend fun deleteAll(
        relatives: List<String>,
        cancel: CancelToken = CancelToken(),
        onProgress: (TransferProgress) -> Unit = {},
    ): MolinaResult<BatchResult>

    // molina-patch26h
    /** Memindahkan item ke Sampah (rename, bisa diurungkan). Root, folder standar, dan Sampah dilindungi. */
    suspend fun trash(relatives: List<String>): MolinaResult<List<TrashItem>>

    suspend fun listTrash(): MolinaResult<List<TrashItem>>

    /** Mengembalikan item Sampah ke lokasi asal; hasilnya path relatif hasil pemulihan. */
    suspend fun restoreTrash(ids: List<String>): MolinaResult<List<String>>

    /** Menghapus permanen item tertentu dari Sampah; hasilnya jumlah yang terhapus. */
    suspend fun purgeTrash(ids: List<String>): MolinaResult<Int>

    suspend fun emptyTrash(): MolinaResult<Int>

    /** Membersihkan item Sampah yang lebih tua dari batas simpan (30 hari). */
    suspend fun purgeExpiredTrash(now: Long = System.currentTimeMillis()): MolinaResult<Int>

    // molina-patch26j
    /** Properti item; folder dihitung rekursif. Urutan hasil sama dengan [relatives]. */
    suspend fun properties(relatives: List<String>): MolinaResult<List<FileProps>>

    /** SHA-256 heksadesimal sebuah berkas biasa. [onProgress] menerima (selesai, total) byte dari thread IO. */
    suspend fun sha256(relative: String, onProgress: (Long, Long) -> Unit = { _, _ -> }): MolinaResult<String>

    /** Berkas yang boleh dibagikan; folder dan isi Sampah dilewati dan dihitung di [ShareTargets.skipped]. */
    suspend fun shareTargets(relatives: List<String>): MolinaResult<ShareTargets>
}

class LocalMolinaStorage(
    private val paths: MolinaPaths = MolinaPaths(),
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) : MolinaStorage {

    /** Root dan folder standar: tidak boleh dihapus atau diganti namanya. */
    private val protectedPaths: Set<File> get() = paths.layout.toSet() + paths.trash

    // molina-patch26h
    private val bin by lazy { TrashBin(paths.root) }

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

    override suspend fun browse(relative: String, options: ListOptions): MolinaResult<ListResult> =
        withContext(dispatchers.io) {
            when (val resolved = PathGuard.resolveInside(paths.root, relative)) {
                is MolinaResult.Failure -> resolved
                is MolinaResult.Success -> molinaCatching {
                    val dir = resolved.value
                    if (!dir.exists()) throw FileNotFoundException()
                    if (!dir.isDirectory) throw IOException("bukan folder")
                    EntryLister.list(dir, options) { !isActive } ?: throw SecurityException()
                }
            }
        }

    override suspend fun createFolder(parentRelative: String, name: String): MolinaResult<FileEntry> =
        withContext(dispatchers.io) {
            onResolved(parentRelative) { FileOperations.createFolder(it, name) }
        }

    override suspend fun createFile(parentRelative: String, name: String): MolinaResult<FileEntry> =
        withContext(dispatchers.io) {
            onResolved(parentRelative) { FileOperations.createFile(it, name) }
        }

    override suspend fun rename(relative: String, newName: String): MolinaResult<FileEntry> =
        withContext(dispatchers.io) {
            onResolved(relative) { FileOperations.rename(it, newName, protectedPaths) }
        }

    override suspend fun delete(relative: String): MolinaResult<Unit> =
        withContext(dispatchers.io) {
            onResolved(relative) { FileOperations.delete(it, protectedPaths) { !isActive } }
        }

    // molina-patch26c
    override suspend fun copy(
        sources: List<String>,
        destRelative: String,
        cancel: CancelToken,
        onProgress: (TransferProgress) -> Unit,
    ): MolinaResult<BatchResult> = withContext(dispatchers.io) {
        onTransferInputs(sources, destRelative) { files, dest ->
            TransferOperations.copy(files, dest, { cancel.isCancelled || !isActive }, onProgress)
        }
    }

    override suspend fun move(
        sources: List<String>,
        destRelative: String,
        cancel: CancelToken,
        onProgress: (TransferProgress) -> Unit,
    ): MolinaResult<BatchResult> = withContext(dispatchers.io) {
        onTransferInputs(sources, destRelative) { files, dest ->
            TransferOperations.move(files, dest, protectedPaths, { cancel.isCancelled || !isActive }, onProgress)
        }
    }

    override suspend fun deleteAll(
        relatives: List<String>,
        cancel: CancelToken,
        onProgress: (TransferProgress) -> Unit,
    ): MolinaResult<BatchResult> = withContext(dispatchers.io) {
        when (val files = resolveAll(relatives)) {
            is MolinaResult.Failure -> files
            is MolinaResult.Success ->
                TransferOperations.deleteAll(files.value, protectedPaths, { cancel.isCancelled || !isActive }, onProgress)
        }
    }

    // molina-patch26h
    override suspend fun trash(relatives: List<String>): MolinaResult<List<TrashItem>> =
        withContext(dispatchers.io) {
            when (val files = resolveAll(relatives)) {
                is MolinaResult.Failure -> files
                is MolinaResult.Success -> bin.trash(files.value, protectedPaths)
            }
        }

    override suspend fun listTrash(): MolinaResult<List<TrashItem>> =
        withContext(dispatchers.io) { molinaCatching { bin.list() } }

    override suspend fun restoreTrash(ids: List<String>): MolinaResult<List<String>> =
        withContext(dispatchers.io) { bin.restore(ids) }

    override suspend fun purgeTrash(ids: List<String>): MolinaResult<Int> =
        withContext(dispatchers.io) { bin.purge(ids) }

    override suspend fun emptyTrash(): MolinaResult<Int> =
        withContext(dispatchers.io) { bin.empty() }

    override suspend fun purgeExpiredTrash(now: Long): MolinaResult<Int> =
        withContext(dispatchers.io) { bin.purgeExpired(now) }

    // molina-patch26j
    override suspend fun properties(relatives: List<String>): MolinaResult<List<FileProps>> =
        withContext(dispatchers.io) {
            when (val files = resolveAll(relatives)) {
                is MolinaResult.Failure -> files
                is MolinaResult.Success -> molinaCatching {
                    files.value.mapIndexed { i, f ->
                        if (!f.exists()) throw FileNotFoundException()
                        PropsReader.read(f, relatives[i], { !isActive })
                    }
                }
            }
        }

    override suspend fun sha256(relative: String, onProgress: (Long, Long) -> Unit): MolinaResult<String> =
        withContext(dispatchers.io) {
            onResolved(relative) { f ->
                molinaCatching {
                    if (!f.exists()) throw FileNotFoundException()
                    PropsReader.sha256(f, { !isActive }, onProgress)
                }
            }
        }

    override suspend fun shareTargets(relatives: List<String>): MolinaResult<ShareTargets> =
        withContext(dispatchers.io) {
            when (val files = resolveAll(relatives)) {
                is MolinaResult.Failure -> files
                is MolinaResult.Success -> molinaCatching {
                    val trash = paths.trash.canonicalFile.path
                    val ok = ArrayList<File>()
                    var skipped = 0
                    for (f in files.value) {
                        val inTrash = f.path == trash || f.path.startsWith(trash + File.separator)
                        if (f.isFile && !inTrash) ok.add(f) else skipped++
                    }
                    ShareTargets(ok, skipped)
                }
            }
        }

    private inline fun onTransferInputs(
        sources: List<String>,
        destRelative: String,
        block: (List<File>, File) -> MolinaResult<BatchResult>,
    ): MolinaResult<BatchResult> {
        val files = when (val r = resolveAll(sources)) {
            is MolinaResult.Failure -> return r
            is MolinaResult.Success -> r.value
        }
        return when (val dest = PathGuard.resolveInside(paths.root, destRelative)) {
            is MolinaResult.Failure -> dest
            is MolinaResult.Success -> block(files, dest.value)
        }
    }

    /** Menyelesaikan semua path relatif; satu path tidak valid menggagalkan seluruh permintaan. */
    private fun resolveAll(relatives: List<String>): MolinaResult<List<File>> {
        val out = ArrayList<File>(relatives.size)
        for (rel in relatives) {
            when (val r = PathGuard.resolveInside(paths.root, rel)) {
                is MolinaResult.Failure -> return r
                is MolinaResult.Success -> out += r.value
            }
        }
        return MolinaResult.Success(out)
    }

    private inline fun <T> onResolved(relative: String, block: (File) -> MolinaResult<T>): MolinaResult<T> =
        when (val resolved = PathGuard.resolveInside(paths.root, relative)) {
            is MolinaResult.Failure -> resolved
            is MolinaResult.Success -> block(resolved.value)
        }

    private fun listDirectory(dir: File): MolinaResult<List<FileEntry>> = molinaCatching {
        if (!dir.exists()) throw FileNotFoundException()
        if (!dir.isDirectory) throw IOException("bukan folder")
        val children = dir.listFiles() ?: throw SecurityException()
        children
            .filter { it.name != MolinaPaths.TRASH_DIR }
            .map { FileEntry(it.name, it.path, it.isDirectory, if (it.isDirectory) 0L else it.length(), it.lastModified()) }
            .sortedWith(compareByDescending<FileEntry> { it.isDirectory }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
}
