// molina-patch26j
package com.molina.files

import com.molina.core.storage.FileProps
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Teks dialog Properti. Murni JVM. */
internal object PropsText {
    private val ID = Locale("id", "ID")

    fun date(ms: Long, zone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("dd/MM/yyyy HH.mm", ID).apply { timeZone = zone }.format(Date(ms))

    fun grouped(bytes: Long): String = String.format(ID, "%,d", bytes)

    fun title(items: List<FileProps>, rootLabel: String = "Molina"): String = // molina-27u
        if (items.size == 1) items[0].name.ifEmpty { rootLabel } else "${items.size} item"

    fun body(items: List<FileProps>, zone: TimeZone = TimeZone.getDefault(), view: View? = null): String = when {
        view != null && items.size == 1 -> rich(items[0], zone, view) // molina-27u
        view != null -> richMulti(items, view)
        items.size == 1 -> single(items[0], zone)
        else -> multi(items)
    }

    /** [molina-27v] Baris (label, nilai) dari [body], untuk lembar bawah Properti. */
    fun rows(items: List<FileProps>, zone: TimeZone = TimeZone.getDefault(), view: View? = null): List<Pair<String, String>> =
        body(items, zone, view).lines().filter { it.isNotBlank() }.map {
            val i = it.indexOf(": ")
            if (i < 0) "" to it else it.substring(0, i) to it.substring(i + 2)
        }

    fun location(path: String, rootLabel: String = "Molina"): String { // molina-27u: label root bisa "Internal"
        if (path.isEmpty()) return "Root $rootLabel"
        val parent = path.substringBeforeLast('/', "")
        return if (parent.isEmpty()) rootLabel else "$rootLabel/$parent"
    }

    /** [molina-27u] Konteks tampilan Properti kaya: label root ("Internal"), dan path absolut root untuk baris Path. */
    data class View(val rootLabel: String, val absRoot: String? = null)

    private val MIME = mapOf(
        "mkv" to "video/x-matroska", "mp4" to "video/mp4", "avi" to "video/x-msvideo", "webm" to "video/webm", "mov" to "video/quicktime",
        "mp3" to "audio/mpeg", "flac" to "audio/flac", "ogg" to "audio/ogg", "wav" to "audio/wav", "m4a" to "audio/mp4",
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "gif" to "image/gif", "webp" to "image/webp",
        "srt" to "application/x-subrip", "ass" to "text/x-ssa", "vtt" to "text/vtt",
        "txt" to "text/plain", "md" to "text/markdown", "json" to "application/json", "xml" to "application/xml", "html" to "text/html",
        "pdf" to "application/pdf", "zip" to "application/zip", "7z" to "application/x-7z-compressed", "rar" to "application/vnd.rar",
        "tar" to "application/x-tar", "gz" to "application/gzip", "apk" to "application/vnd.android.package-archive",
        "sh" to "text/x-shellscript", "kt" to "text/x-kotlin", "py" to "text/x-python",
    )

    fun extension(name: String): String { // molina-27u: ".bashrc" tanpa ekstensi, ".a.mkv" = mkv
        val dot = name.lastIndexOf('.')
        return if (dot <= 0 || dot == name.length - 1) "" else name.substring(dot + 1).lowercase(Locale.ROOT)
    }

    fun mime(name: String): String? = MIME[extension(name)]

    private fun access(p: FileProps): String {
        val parts = arrayListOf<String>()
        parts += if (p.canRead) "baca" else "tanpa baca"
        parts += if (p.canWrite) "tulis" else "hanya-baca"
        if (p.canExecute) parts += "jalankan"
        return parts.joinToString(", ")
    }

    private fun rich(p: FileProps, zone: TimeZone, v: View): String {
        val ext = extension(p.name)
        val kind = when {
            p.isSymlink -> "Tautan simbolik"
            p.isDirectory -> "Folder"
            ext.isNotEmpty() -> "Berkas ${ext.uppercase(Locale.ROOT)}"
            else -> "Berkas"
        }
        val lines = arrayListOf("Jenis: $kind")
        if (!p.isDirectory && !p.isSymlink) mime(p.name)?.let { lines += "Tipe: $it" }
        lines += "Ukuran: ${sizeLine(p.sizeBytes)}"
        if (p.isDirectory) {
            lines += "Isi: ${p.fileCount} berkas, ${p.folderCount} folder" + (if (p.hiddenCount > 0) " (${p.hiddenCount} tersembunyi)" else "")
        }
        lines += "Diubah: ${date(p.lastModifiedMs, zone)}"
        lines += "Akses: ${access(p)}"
        lines += "Tersembunyi: " + (if (p.name.startsWith('.')) "ya" else "tidak")
        p.linkTarget?.let { lines += "Target tautan: $it" }
        lines += "Lokasi: " + location(p.path, v.rootLabel)
        v.absRoot?.let { lines += "Path: " + (if (p.path.isEmpty()) it else it.trimEnd('/') + "/" + p.path) }
        return lines.joinToString("\n")
    }

    private fun richMulti(items: List<FileProps>, v: View): String {
        val dirs = items.count { it.isDirectory }
        val own = items.size - dirs
        val files = own + items.sumOf { it.fileCount }
        val folders = dirs + items.sumOf { it.folderCount }
        val bytes = items.sumOf { it.sizeBytes }
        val lines = arrayListOf("Item: ${items.size} ($own berkas, $dirs folder)", "Ukuran total: ${sizeLine(bytes)}", "Isi: $files berkas, $folders folder")
        val parents = items.map { it.path.substringBeforeLast('/', "") }.distinct()
        if (parents.size == 1) lines += "Lokasi: " + (if (parents[0].isEmpty()) v.rootLabel else v.rootLabel + "/" + parents[0])
        items.maxByOrNull { it.lastModifiedMs }?.let { lines += "Terbaru diubah: ${it.name.ifEmpty { v.rootLabel }}" }
        if (items.any { !it.canWrite }) lines += "Akses: ada item hanya-baca"
        return lines.joinToString("\n")
    }

    private fun sizeLine(bytes: Long): String = "${FileSizeFormat.format(bytes)} (${grouped(bytes)} B)"

    private fun single(p: FileProps, zone: TimeZone): String {
        val kind = when {
            p.isSymlink -> "Tautan"
            p.isDirectory -> "Folder"
            else -> "Berkas"
        }
        val lines = arrayListOf("Jenis: $kind", "Ukuran: ${sizeLine(p.sizeBytes)}")
        if (p.isDirectory) lines += "Isi: ${p.fileCount} berkas, ${p.folderCount} folder"
        lines += "Diubah: ${date(p.lastModifiedMs, zone)}"
        lines += "Akses: " + (if (p.canRead) "baca" else "tanpa baca") + (if (p.canWrite) ", tulis" else ", hanya-baca")
        lines += "Lokasi: " + location(p.path)
        return lines.joinToString("\n")
    }

    private fun multi(items: List<FileProps>): String {
        val dirs = items.count { it.isDirectory }
        val files = items.size - dirs + items.sumOf { it.fileCount }
        val folders = dirs + items.sumOf { it.folderCount }
        val bytes = items.sumOf { it.sizeBytes }
        return "Item: ${items.size}\nUkuran total: ${sizeLine(bytes)}\nIsi: $files berkas, $folders folder"
    }
}
