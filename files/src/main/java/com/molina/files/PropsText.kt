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

    fun title(items: List<FileProps>): String =
        if (items.size == 1) items[0].name.ifEmpty { "Molina" } else "${items.size} item"

    fun body(items: List<FileProps>, zone: TimeZone = TimeZone.getDefault()): String =
        if (items.size == 1) single(items[0], zone) else multi(items)

    fun location(path: String): String {
        if (path.isEmpty()) return "Root Molina"
        val parent = path.substringBeforeLast('/', "")
        return if (parent.isEmpty()) "Molina" else "Molina/$parent"
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
