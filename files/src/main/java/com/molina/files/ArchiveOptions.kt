// molina-patch26k2
package com.molina.files

import com.molina.core.storage.ArchiveFormat

/** Pilihan di dialog Kompres. Murni, tanpa Android. */
internal object ArchiveOptions {
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
