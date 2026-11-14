package com.molina.core.common

import java.io.File

/** Tata letak folder publik Molina. Root dapat diganti (mis. folder sementara di tes). */
class MolinaPaths(val root: File = File(DEFAULT_ROOT)) {
    val logs: File get() = File(root, "logs")
    val terminal: File get() = File(root, "terminal")
    val cinema: File get() = File(root, "cinema")

    /** molina-patch26h: Sampah di dalam root (rename satu volume, instan). Dibuat saat item pertama dihapus. */
    val trash: File get() = File(root, TRASH_DIR)

    /** Urutan pembuatan: root lebih dulu. */
    val layout: List<File> get() = listOf(root, logs, terminal, cinema)

    companion object {
        const val DEFAULT_ROOT = "/storage/emulated/0/Molina"
        const val TRASH_DIR = ".molina-trash"
    }
}
