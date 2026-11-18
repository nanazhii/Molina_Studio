package com.molina.core.common

import java.io.File

/** Tata letak folder publik Molina. Root dapat diganti (mis. folder sementara di tes). */
class MolinaPaths(val root: File = File(DEFAULT_ROOT)) {
    val logs: File get() = File(root, "logs")
    val terminal: File get() = File(root, "terminal")

    /** molina-patch26h: Sampah di dalam root (rename satu volume, instan). Dibuat saat item pertama dihapus. */
    val trash: File get() = File(root, TRASH_DIR)

    /** Cache entri arsip yang dibuka satu per satu (Patch 26k-5b). 26k5b:e01 */
    val arsipCache: File get() = File(root, ARSIP_CACHE_DIR)

    /** Urutan pembuatan: root lebih dulu. */
    val layout: List<File> get() = listOf(root, logs, terminal)

    companion object {
        const val DEFAULT_ROOT = "/storage/emulated/0/Molina"
        const val INTERNAL_ROOT = "/storage/emulated/0" // molina-27j: root tab Berkas (1-1 dengan file manager Android)
        const val TRASH_DIR = ".molina-trash"
        const val ARSIP_CACHE_DIR = ".arsip-cache" // 26k5b:e02
    }
}
