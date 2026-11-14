// molina-patch26k2
package com.molina.files

import com.molina.core.storage.ArchiveFormat
import com.molina.core.storage.ConflictPolicy // 26k4:e41
import com.molina.core.storage.ExtractTarget
import java.util.Locale

/** Pilihan di dialog Kompres. Murni, tanpa Android. */
internal object ArchiveOptions {
    // 26k4:e42
    /** Tujuan di dialog opsi Ekstrak. */
    val TARGETS: List<ExtractTarget> = listOf(ExtractTarget.HERE, ExtractTarget.NAMED_FOLDER)

    /** Kebijakan bila berkas sudah ada; yang pertama = bawaan paling aman (tidak kehilangan data). */
    val POLICIES: List<ConflictPolicy> = listOf(ConflictPolicy.RENAME, ConflictPolicy.SKIP, ConflictPolicy.OVERWRITE)

    fun targetLabel(t: ExtractTarget): String = when (t) {
        ExtractTarget.HERE -> "Di sini"
        ExtractTarget.NAMED_FOLDER -> "Folder bernama arsip"
    }

    fun policyLabel(p: ConflictPolicy): String = when (p) {
        ConflictPolicy.RENAME -> "Ganti nama"
        ConflictPolicy.SKIP -> "Lewati"
        ConflictPolicy.OVERWRITE -> "Timpa"
    }

    /** Nama kebijakan huruf kecil untuk log (rename, skip, overwrite). */
    fun policyName(p: ConflictPolicy): String = p.name.lowercase(Locale.ROOT)

    /** Format yang ditawarkan, berurutan. gz/bz2/xz tunggal hanya bisa diekstrak. */
    val FORMATS: List<ArchiveFormat> = listOf(
        ArchiveFormat.ZIP, ArchiveFormat.SEVEN_Z, ArchiveFormat.TAR_GZ,
        ArchiveFormat.TAR_XZ, ArchiveFormat.TAR_BZ2, ArchiveFormat.TAR,
    )

    /**
     * Pasangan (level, label) untuk [fmt]; kosong bila format tanpa level (tar). "Simpan" (tanpa kompresi)
     * hanya untuk ZIP dan 7z. Level kembar dibuang (7z: Normal = maksimum).
     */
    fun levels(fmt: ArchiveFormat): List<Pair<Int, String>> {
        if (!fmt.hasLevel) return emptyList()
        val out = LinkedHashMap<Int, String>()
        if (fmt == ArchiveFormat.ZIP || fmt == ArchiveFormat.SEVEN_Z) out[0] = "Simpan"
        out.putIfAbsent(maxOf(fmt.minLevel, 1), "Cepat")
        out.putIfAbsent(fmt.defaultLevel, "Normal")
        out.putIfAbsent(fmt.maxLevel, "Maksimal")
        return out.map { it.key to it.value }
    }
}
