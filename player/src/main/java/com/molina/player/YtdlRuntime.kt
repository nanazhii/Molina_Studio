package com.molina.player

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * molina-ytdl: runtime yt-dlp (CPython bionic + zipapp + quickjs) yang dipasang pipeline CI
 * ke jniLibs dan assets. mpv memakai ytdl_hook bawaan; objek ini hanya menyiapkan skrip
 * pembungkus dan opsi mpv-nya.
 */
internal object YtdlRuntime {
    private const val TAG = "ytdl"
    private const val ZIP_ASSET = "ytdl/ytdl-data.zip"
    private const val PY_EXEC = "libpython3_exec.so"
    private const val QJS_EXEC = "libqjs_exec.so"
    private const val WRAPPER = "ytdl-run.sh"

    /** Menyiapkan runtime. Mengembalikan skrip pembungkus, atau null bila APK tanpa yt-dlp. */
    @Synchronized
    fun prepare(context: Context): File? {
        val app = context.applicationContext
        val nativeDir = File(app.applicationInfo.nativeLibraryDir)
        if (!File(nativeDir, PY_EXEC).exists() || !File(nativeDir, QJS_EXEC).exists()) return null
        val root = File(app.filesDir, "ytdl")
        return try {
            val stamp = readStamp(app) ?: return null
            val stampFile = File(root, "STAMP")
            val ready = stampFile.exists() && stampFile.readText().trim() == stamp &&
                File(root, "yt-dlp").exists()
            if (!ready) extract(app, root)
            val wrapper = File(root, WRAPPER)
            val script = buildScript(app, nativeDir, root)
            if (!wrapper.exists() || wrapper.readText() != script) wrapper.writeText(script)
            wrapper.setExecutable(true, false)
            wrapper
        } catch (e: Exception) {
            Log.e(TAG, "gagal menyiapkan runtime yt-dlp", e)
            null
        }
    }

    /** Dipanggil sebelum MPVLib.init(): arahkan ytdl_hook ke skrip pembungkus dan quickjs. */
    fun applyOptions(context: Context) {
        val wrapper = prepare(context) ?: return
        val qjs = File(context.applicationContext.applicationInfo.nativeLibraryDir, QJS_EXEC)
        MPVLib.setOptionString("ytdl", "yes")
        MPVLib.setOptionString("script-opts", "ytdl_hook-ytdl_path=${wrapper.path}")
        MPVLib.setOptionString("ytdl-raw-options", "js-runtimes=quickjs:${qjs.path}")
        // batasi 1080p agar nyaman di perangkat lama; mpv.conf pengguna tetap bisa menimpa
        MPVLib.setOptionString("ytdl-format", "bv*[height<=1080]+ba/b[height<=1080]/b")
    }

    private fun readStamp(app: Context): String? =
        ZipInputStream(app.assets.open(ZIP_ASSET).buffered()).use { zin ->
            val e = zin.nextEntry
            if (e != null && e.name == "STAMP") zin.readBytes().toString(Charsets.UTF_8).trim() else null
        }

    private fun extract(app: Context, root: File) {
        val tmp = File(app.filesDir, "ytdl.new")
        tmp.deleteRecursively()
        tmp.mkdirs()
        val tmpPath = tmp.canonicalPath + File.separator
        ZipInputStream(app.assets.open(ZIP_ASSET).buffered()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                val out = File(tmp, e.name)
                if (!out.canonicalPath.startsWith(tmpPath)) throw SecurityException(e.name)
                if (e.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zin.copyTo(it) }
                }
                e = zin.nextEntry
            }
        }
        root.deleteRecursively()
        if (!tmp.renameTo(root)) throw IOException("rename ytdl.new gagal")
    }

    private fun sq(s: String) = "'" + s.replace("'", "'\\''") + "'"

    private fun buildScript(app: Context, nativeDir: File, root: File): String = buildString {
        append("#!/system/bin/sh\n")
        append("export PYTHONHOME=").append(sq(File(root, "py").path)).append('\n')
        append("export PYTHONDONTWRITEBYTECODE=1\n")
        append("export PYTHONNOUSERSITE=1\n")
        append("export LD_LIBRARY_PATH=").append(sq(nativeDir.path))
            .append("\${LD_LIBRARY_PATH:+:\$LD_LIBRARY_PATH}\n")
        append("export SSL_CERT_FILE=").append(sq(File(app.filesDir, "cacert.pem").path)).append('\n')
        append("export HOME=").append(sq(app.filesDir.path)).append('\n')
        append("export TMPDIR=").append(sq(app.cacheDir.path)).append('\n')
        append("export XDG_CACHE_HOME=").append(sq(app.cacheDir.path)).append('\n')
        append("exec ").append(sq(File(nativeDir, PY_EXEC).path)).append(' ')
            .append(sq(File(root, "yt-dlp").path)).append(" \"\$@\"\n")
    }
}
