// molina-patch26k2
package com.molina.files

import com.molina.core.storage.ArchiveEntryInfo
import com.molina.core.storage.ArchiveNames
import java.util.Locale

/** Memilih berkas subtitle dari daftar isi arsip untuk "Ekstrak subtitle di sini". Murni, tanpa Android. */
internal object ArchiveSubs {
    private val EXT = setOf("srt", "ass", "ssa", "vtt", "sub", "idx", "sup", "smi")

    /** Batas jumlah subtitle sekali ekstrak (arsip subtitle satu musim biasanya puluhan). */
    const val MAX = 300

    fun isSubtitle(name: String): Boolean = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in EXT

    /**
     * Nama entri bersih (siap jadi kunci `only`) untuk berkas subtitle. Folder, sampah macOS (`__MACOSX/`,
     * `._nama`), dan nama tidak aman dilewati. Struktur folder di dalam arsip dipertahankan saat ekstrak.
     */
    fun pick(entries: List<ArchiveEntryInfo>): Set<String> {
        val out = LinkedHashSet<String>()
        for (e in entries) {
            if (e.isDirectory) continue
            val clean = ArchiveNames.cleanEntry(e.name) ?: continue
            if (clean.isEmpty()) continue
            val segs = clean.split('/')
            if (segs.first() == "__MACOSX" || segs.last().startsWith("._")) continue
            if (!isSubtitle(segs.last())) continue
            out.add(clean)
            if (out.size >= MAX) break
        }
        return out
    }
}
