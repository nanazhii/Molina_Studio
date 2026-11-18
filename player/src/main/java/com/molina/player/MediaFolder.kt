package com.molina.player

import java.io.File

/**
 * Daftar media dalam folder untuk tombol Buka > Folder / Folder dalam folder di Home.
 * Murni java.io (tanpa Android) dan sinkron: panggil dari Dispatchers.IO.
 * Daftar putar dan gambar tidak ikut, supaya sampul (cover.jpg) tidak menjadi slide di tengah playlist.
 */
internal object MediaFolder {
    const val MAX_ENTRIES = 1000
    private const val MAX_DEPTH = 6

    private val EXCLUDED = setOf(
        "cue", "m3u", "m3u8", "pls", "strm", "vlc",
        "apng", "avif", "bmp", "exr", "gif", "heic", "heif", "j2c", "j2k", "jfif", "jp2", "jpc",
        "jpe", "jpeg", "jpg", "jpg2", "png", "qoi", "tga", "tif", "tiff", "webp",
    )
    private val NUM_PARTS = Regex("\\d+|\\D+")

    class Scan(val paths: List<String>, val truncated: Boolean)

    fun isMedia(f: File): Boolean =
        f.isFile && !f.name.startsWith(".") &&
            f.extension.lowercase().let { it in Utils.MEDIA_EXTENSIONS && it !in EXCLUDED }

    /** Urutan alami: "ep2" sebelum "ep10". */
    fun naturalCompare(a: String, b: String): Int {
        val x = NUM_PARTS.findAll(a.lowercase()).map { it.value }.toList()
        val y = NUM_PARTS.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]
            val q = y[i]
            val c = if (p[0].isDigit() && q[0].isDigit()) {
                val pt = p.trimStart('0')
                val qt = q.trimStart('0')
                if (pt.length != qt.length) pt.length - qt.length else pt.compareTo(qt)
            } else {
                p.compareTo(q)
            }
            if (c != 0) return c
        }
        return x.size - y.size
    }

    /**
     * Media di [dir]; bila [deep], subfolder ikut (berkas folder itu dulu, lalu tiap subfolder berurutan alami).
     * Dibatasi [MAX_ENTRIES] berkas dan kedalaman 6; folder tersembunyi dan putaran symlink dilewati.
     */
    fun scan(dir: File, deep: Boolean): Scan {
        val out = ArrayList<String>()
        val seen = HashSet<String>()
        var truncated = false

        fun walk(d: File, depth: Int) {
            if (truncated) return
            val canon = runCatching { d.canonicalPath }.getOrNull() ?: return
            if (!seen.add(canon)) return
            val children = d.listFiles()
                ?.filter { !it.name.startsWith(".") }
                ?.sortedWith { a, b -> naturalCompare(a.name, b.name) }
                ?: return
            for (f in children) {
                if (!isMedia(f)) continue
                if (out.size >= MAX_ENTRIES) {
                    truncated = true
                    return
                }
                out.add(f.path)
            }
            if (!deep || depth >= MAX_DEPTH) return
            for (f in children) {
                if (truncated) return
                if (f.isDirectory) walk(f, depth + 1)
            }
        }

        walk(dir, 0)
        return Scan(out, truncated)
    }

    /** URI playlist memory:// untuk [paths]; null bila kosong. Nama berkas bermasalah (baris baru) dilewati. */
    fun memoryPlaylist(paths: List<String>): String? {
        val ok = paths.filter { p -> p.none { c -> c == '\n' || c == '\r' || c == '\u0000' } }
        if (ok.isEmpty()) return null
        return runCatching { Utils.makeMemoryPlaylist(ok) }.getOrNull()
    }
}
