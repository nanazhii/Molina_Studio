// molina-27q
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
 * [molina-27q] Penulis 7z bersandi: menjalankan `7z a` di Debian PRoot (meniru DebianArchive.create repo lama).
 * - Sandi hanya lewat stdin, tidak pernah di argumen proses atau log.
 * - Hanya folder sumber (/mnt/molina-in/<i>) dan staging (/mnt/molina-out) yang di-bind; bukan seluruh penyimpanan.
 * - Pembatalan mematikan proot (--kill-on-exit ikut mematikan 7z).
 * Blocking: dipanggil dari dispatcher IO oleh ArchiveEngine.
 */
class DebianSevenZip(private val context: Context) : SevenZipWriter {

    private fun layout() = DebianLayout(context.filesDir)

    private fun binary(l: DebianLayout): String? = SevenZipCommand.GUEST_BINARIES.firstOrNull { g ->
        val f = File(l.rootfsDir, g.removePrefix("/"))
        f.exists() || Files.isSymbolicLink(f.toPath())
    }

    override fun unavailableReason(): String? {
        val l = layout()
        if (!l.isReady()) return "Debian belum terpasang. Pasang lewat Terminal terlebih dulu."
        return if (binary(l) == null) SevenZipCommand.HINT_INSTALL else null
    }

    override fun create(job: SevenZipJob, cancelled: () -> Boolean, onPercent: (Int) -> Unit) {
        val l = layout()
        val bin = binary(l) ?: throw IOException(SevenZipCommand.HINT_INSTALL)
        val stdin = SevenZipCommand.stdinFor(job.password) ?: throw IOException("Kata sandi wajib")

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
        val proc = try {
            pb.start()
        } catch (e: IOException) {
            throw IOException("Gagal memulai proot", e)
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
    }
}
