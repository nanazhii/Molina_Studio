// molina-patch10a
package com.molina.terminal.proot

/**
 * Pin rootfs Debian 13.7 (trixie) arm64 slim dari debuerreotype/docker-debian-artifacts.
 * URL dikunci ke commit, bukan cabang. Hash dihitung dan dicocokkan sendiri (2026-11-12).
 */
object RootfsPin {
    const val DEBIAN_VERSION = "13.7"
    const val URL =
        "https://raw.githubusercontent.com/debuerreotype/docker-debian-artifacts/" + // gate:allow rootfs Debian resmi, dipin SHA-256
            "3b26c3995c7fae47d95ba678632a3520e97054f7/trixie/slim/oci/blobs/rootfs.tar.gz"
    const val SHA256 = "acd43e9bebc37bc4ba7a5f47bd7598913337803e53d468f37d940f11f5d5f1ef"
    const val SIZE_BYTES = 30_214_037L

    /** Jumlah entri tar; hanya untuk perkiraan progres ekstraksi. */
    const val ENTRY_COUNT = 3266

    /** Ruang kosong minimum: arsip (30 MB) + hasil ekstrak (~100 MB) + cadangan. */
    const val MIN_FREE_BYTES = 350L * 1024 * 1024
}
