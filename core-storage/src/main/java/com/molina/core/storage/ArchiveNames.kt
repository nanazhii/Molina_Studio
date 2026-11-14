// molina-patch26k
package com.molina.core.storage

import java.io.File
import java.util.Locale

/**
 * Logika nama/jalur arsip yang murni (tanpa pustaka arsip). Semua nama entri dari arsip dianggap
 * tidak tepercaya: [cleanEntry] menolak "..", [safeTarget] memeriksa jalur kanonik.
 */
object ArchiveNames {
    /** Urutan penting: sufiks majemuk (.tar.gz) harus dicek sebelum sufiks tunggal (.gz). */
    private val SUFFIXES: List<Pair<String, ArchiveFormat>> = listOf(
        ".tar.gz" to ArchiveFormat.TAR_GZ, ".tgz" to ArchiveFormat.TAR_GZ,
        ".tar.bz2" to ArchiveFormat.TAR_BZ2, ".tbz2" to ArchiveFormat.TAR_BZ2, ".tbz" to ArchiveFormat.TAR_BZ2,
        ".tar.xz" to ArchiveFormat.TAR_XZ, ".txz" to ArchiveFormat.TAR_XZ,
        ".tar" to ArchiveFormat.TAR, ".7z" to ArchiveFormat.SEVEN_Z, ".zip" to ArchiveFormat.ZIP,
        ".gz" to ArchiveFormat.GZ, ".bz2" to ArchiveFormat.BZ2, ".xz" to ArchiveFormat.XZ,
    )

    private fun match(name: String): Pair<String, ArchiveFormat>? {
        val l = name.lowercase(Locale.ROOT)
        return SUFFIXES.firstOrNull { l.endsWith(it.first) && l.length > it.first.length }
    }

    fun detect(name: String): ArchiveFormat? = match(name)?.second

    /** Nama tanpa sufiks arsip ("a.tar.gz" -> "a", "notes.txt.gz" -> "notes.txt"); "Ekstrak" bila kosong. */
    fun stem(name: String): String {
        val m = match(name) ?: return name.ifEmpty { "Ekstrak" }
        return name.substring(0, name.length - m.first.length).ifEmpty { "Ekstrak" }
    }

    /** Nama subfolder tujuan. Untuk gz/bz2/xz tunggal ekstensi isi dibuang ("notes.txt.gz" -> "notes"). */
    fun folderName(name: String): String {
        val s = stem(name)
        val fmt = detect(name)
        if (fmt != null && fmt.isSingle) {
            val cut = s.substringBeforeLast('.', s)
            if (cut.isNotEmpty()) return cut
        }
        return s
    }

    fun utf8Size(s: String): Int = s.toByteArray(Charsets.UTF_8).size

    /**
     * Bersihkan nama entri: pemisah Windows jadi "/", awalan "/" dan "./" dibuang, segmen kosong dan "."
     * dilewati. null bila ada segmen ".." atau karakter NUL (tidak aman); "" bila entri hanya akar (abaikan).
     */
    fun cleanEntry(raw: String): String? {
        if (raw.indexOf('\u0000') >= 0) return null
        val parts = ArrayList<String>()
        for (seg in raw.replace('\\', '/').split('/')) {
            if (seg.isEmpty() || seg == ".") continue
            if (seg == "..") return null
            parts.add(seg)
        }
        return parts.joinToString("/")
    }

    /** true bila ada segmen nama (bukan seluruh jalur) yang lebih dari batas byte sistem berkas. */
    fun segmentTooLong(clean: String): Boolean =
        clean.split('/').any { utf8Size(it) > NameRules.MAX_BYTES }

    /** Apakah entri [clean] termasuk pilihan [only]? null = semua. Kunci folder berakhiran "/" dan mencakup isinya. */
    fun selected(clean: String, isDir: Boolean, only: Set<String>?): Boolean {
        if (only == null) return true
        val key = if (isDir) clean.trimEnd('/') + "/" else clean
        if (key in only) return true
        for (o in only) if (o.endsWith("/") && key.startsWith(o)) return true
        return false
    }

    /** Jalur tujuan entri di bawah [base]; null bila jalur kanonik keluar dari [base]. */
    fun safeTarget(base: File, clean: String): File? {
        val root = base.canonicalFile
        val out = File(root, clean).canonicalFile
        return if (out.path == root.path || out.path.startsWith(root.path + File.separator)) out else null
    }

    /** "nama (1)", "nama (2)", ... untuk folder hasil ekstrak (titik di nama bukan pemisah ekstensi). */
    fun uniqueFolder(dir: File, name: String): File {
        var f = File(dir, name)
        var i = 1
        while (NameRules.present(f)) {
            f = File(dir, "$name ($i)")
            i++
        }
        return f
    }

    /** "nama.ext", "nama (1).ext", ... sampai belum dipakai (tautan rusak juga dianggap memakai nama). */
    fun uniqueOutput(dir: File, base: String, ext: String): File {
        var f = File(dir, base + ext)
        var i = 1
        while (NameRules.present(f)) {
            f = File(dir, "$base ($i)$ext")
            i++
        }
        return f
    }

    /** Nama dasar: dipangkas, dan ekstensi format yang sama di belakangnya dibuang ("a.zip" + ZIP -> "a"). */
    fun cleanBaseName(raw: String, fmt: ArchiveFormat): String {
        val t = raw.trim()
        return if (t.length > fmt.ext.length && t.lowercase(Locale.ROOT).endsWith(fmt.ext)) {
            t.substring(0, t.length - fmt.ext.length).trim()
        } else {
            t
        }
    }

    /** Alasan nama dasar [clean] (sudah lewat [cleanBaseName]) tidak valid, atau null bila valid. */
    fun baseNameProblem(clean: String, fmt: ArchiveFormat): ArchiveFailure? = when {
        clean.isEmpty() || clean == "." || clean == ".." -> ArchiveFailure.INVALID_NAME
        clean.indexOf('/') >= 0 || clean.indexOf('\\') >= 0 || clean.indexOf('\u0000') >= 0 -> ArchiveFailure.INVALID_NAME
        utf8Size(clean + fmt.ext) > NameRules.MAX_BYTES -> ArchiveFailure.NAME_TOO_LONG
        else -> null
    }

    /** Nama dasar bawaan: satu item = nama folder / nama berkas tanpa ekstensi; banyak item = nama folder induk atau "Arsip". */
    fun defaultBaseName(singleName: String?, singleIsDir: Boolean, parentName: String): String {
        if (singleName != null) {
            if (singleIsDir) return singleName
            val dot = singleName.lastIndexOf('.')
            return if (dot > 0) singleName.substring(0, dot) else singleName
        }
        return parentName.ifEmpty { "Arsip" }
    }
}
