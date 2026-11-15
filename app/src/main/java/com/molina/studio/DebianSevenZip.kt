// molina-27q, molina-27r
package com.molina.studio

import android.content.Context
import android.os.Build
import com.molina.core.common.SevenZipCommand
import com.molina.core.storage.SevenZipFailure
import com.molina.core.storage.SevenZipJob
import com.molina.core.storage.SevenZipWriter
import com.molina.terminal.proot.DebianLayout
import com.molina.terminal.proot.ProotCompat
import com.molina.terminal.proot.ProotExec
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.file.Files

/**
 * Penulis 7z bersandi. Dua jalur, bawaan APK didahulukan:
 * - [molina-27r] LANGSUNG: `lib7zz.so` (7-Zip 26.05 statis, ditambal seccomp Android 8) di nativeLibraryDir, tanpa Debian.
 * - [molina-27q] CADANGAN: `7z a` di Debian PRoot (paket `7zip`), dipakai hanya bila biner bawaan tidak ada/tidak bisa dieksekusi.
 * Dua jalur: sandi hanya lewat stdin, tidak pernah di argumen proses atau log; pembatalan mematikan prosesnya.
 * Jalur Debian juga hanya mem-bind folder sumber (/mnt/molina-in/<i>) dan staging (/mnt/molina-out). Jalur langsung memakai
 * jalur host apa adanya: sumbernya berkas milik pengguna sendiri (bukan arsip tak tepercaya), jadi tidak butuh sandbox bind.
 * Blocking: dipanggil dari dispatcher IO oleh ArchiveEngine.
 */
class DebianSevenZip(private val context: Context) : SevenZipWriter {

    private fun layout() = DebianLayout(context.filesDir)

    /** [molina-27r] Biner bawaan APK; null bila tidak ada atau tidak bisa dieksekusi (mis. paket tanpa ekstraksi native). */
    private fun bundled(): File? =
        File(context.applicationInfo.nativeLibraryDir, BUNDLED_LIB).takeIf { it.isFile && it.canExecute() }

    private fun binary(l: DebianLayout): String? = SevenZipCommand.GUEST_BINARIES.firstOrNull { g ->
        val f = File(l.rootfsDir, g.removePrefix("/"))
        f.exists() || Files.isSymbolicLink(f.toPath())
    }

    override fun unavailableReason(): String? {
        if (bundled() != null) return null
        val l = layout()
        if (!l.isReady()) return "Debian belum terpasang. Pasang lewat Terminal terlebih dulu."
        return if (binary(l) == null) SevenZipCommand.HINT_INSTALL else null
    }

    override fun create(job: SevenZipJob, cancelled: () -> Boolean, onPercent: (Int) -> Unit) {
        val stdin = SevenZipCommand.stdinFor(job.password) ?: throw IOException("Kata sandi wajib")
        val lib = bundled()
        val pb = if (lib != null) directProcess(lib, job) else debianProcess(job)
        execute(pb, stdin, cancelled, onPercent)
    }

    /** [molina-27r] `lib7zz.so` langsung dengan jalur host; lingkungan bersih, TMPDIR di cache aplikasi. */
    private fun directProcess(lib: File, job: SevenZipJob): ProcessBuilder {
        val sources = job.sources.map { f ->
            val parent = (f.absoluteFile.parentFile ?: throw IOException("Folder sumber tidak ditemukan")).canonicalFile
            File(parent, f.name).path
        }
        val out = File(job.stageDir.canonicalFile, job.output.name).path
        val args = SevenZipCommand.createArgs(
            out, sources, job.level, hasPassword = true, encryptNames = job.encryptNames, excludes = job.excludes,
        )
        val tmp = File(context.cacheDir, "7z-tmp").apply { mkdirs() }
        val pb = ProcessBuilder(listOf(lib.path) + args).directory(context.filesDir).redirectErrorStream(true)
        pb.environment().apply {
            clear()
            put("TMPDIR", tmp.absolutePath)
            put("PATH", "/system/bin")
            put("HOME", context.filesDir.absolutePath)
            put("LANG", "C.UTF-8")
            put("LC_ALL", "C.UTF-8")
        }
        return pb
    }

    /** [molina-27q] `7z a` di Debian PRoot dengan bind berlingkup (jalur cadangan). */
    private fun debianProcess(job: SevenZipJob): ProcessBuilder {
        val l = layout()
        val bin = binary(l) ?: throw IOException(SevenZipCommand.HINT_INSTALL)

        // Satu bind per folder induk sumber; jalur di arsip tetap hanya nama item.
        val parents = LinkedHashMap<String, Int>()
        val guestSources = ArrayList<String>()
        for (f in job.sources) {
            val parent = (f.absoluteFile.parentFile ?: throw IOException("Folder sumber tidak ditemukan")).canonicalFile
            if (!SevenZipCommand.bindHostOk(parent.path)) throw IOException("Jalur sumber memuat karakter yang tidak didukung")
            val idx = parents.getOrPut(parent.path) { parents.size }
            guestSources.add(SevenZipCommand.GUEST_IN + "/" + idx + "/" + f.name)
        }
        if (parents.size > SevenZipCommand.MAX_SOURCE_DIRS) throw IOException("Sumber berasal dari terlalu banyak folder berbeda")
        val stage = job.stageDir.canonicalFile
        if (!SevenZipCommand.bindHostOk(stage.path)) throw IOException("Jalur tujuan memuat karakter yang tidak didukung")

        // Titik mount harus ada di rootfs sebelum proot berjalan.
        for (i in parents.values) File(l.rootfsDir, "mnt/molina-in/$i").mkdirs()
        File(l.rootfsDir, "mnt/molina-out").mkdirs()

        val args = SevenZipCommand.createArgs(
            SevenZipCommand.GUEST_OUT + "/" + job.output.name, guestSources, job.level,
            hasPassword = true, encryptNames = job.encryptNames, excludes = job.excludes,
        )
        val binds = parents.entries.map { it.key to (SevenZipCommand.GUEST_IN + "/" + it.value) } +
            (stage.path to SevenZipCommand.GUEST_OUT)
        val command = listOf(l.prootBin.path) + SevenZipCommand.prootArgsScoped(l.rootfsDir.path, binds, bin, args)

        val pb = ProcessBuilder(command).directory(context.filesDir).redirectErrorStream(true)
        pb.environment().apply {
            clear()
            putAll(
                ProotExec.env(
                    l, context.filesDir.absolutePath,
                    noSeccomp = Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1, compat = ProotCompat.enabled,
                ),
            )
        }
        return pb
    }

    /** Menjalankan [pb], mengirim sandi lewat stdin, membaca progres, dan memetakan kode keluar ke [SevenZipFailure]. */
    private fun execute(pb: ProcessBuilder, stdin: ByteArray, cancelled: () -> Boolean, onPercent: (Int) -> Unit) {
        val proc = try {
            pb.start()
        } catch (e: IOException) {
            throw IOException("Gagal memulai 7z", e)
        }
        val watcher = Thread {
            try {
                while (proc.isAlive) {
                    if (cancelled()) {
                        proc.destroyForcibly()
                        break
                    }
                    Thread.sleep(150)
                }
            } catch (ignored: InterruptedException) {
            }
        }.apply {
            isDaemon = true
            start()
        }
        val tail = StringBuilder()
        try {
            try {
                proc.outputStream.use { it.write(stdin) }
            } catch (ignored: IOException) {
                // proses mati lebih dulu; alasan nyata muncul dari kode keluar
            }
            InputStreamReader(proc.inputStream, Charsets.UTF_8).use { r ->
                val buf = CharArray(4096)
                while (true) {
                    val n = r.read(buf)
                    if (n < 0) break
                    val s = String(buf, 0, n)
                    SevenZipCommand.parseProgress(s)?.let(onPercent)
                    tail.append(s)
                    if (tail.length > TAIL_MAX) tail.delete(0, tail.length - TAIL_MAX)
                }
            }
            val exit = proc.waitFor()
            if (cancelled()) throw SevenZipFailure(SevenZipCommand.Failure.CANCELLED)
            if (exit != 0) throw SevenZipFailure(SevenZipCommand.classifyCreate(exit, tail.toString(), true))
        } finally {
            watcher.interrupt()
            proc.destroy()
        }
    }

    private companion object {
        const val TAIL_MAX = 8192

        /** [molina-27r] Nama berkas di nativeLibraryDir; harus sama dengan `app/src/main/jniLibs/arm64-v8a/lib7zz.so`. */
        const val BUNDLED_LIB = "lib7zz.so"
    }
}
