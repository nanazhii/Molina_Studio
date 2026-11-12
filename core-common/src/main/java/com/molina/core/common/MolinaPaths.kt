package com.molina.core.common

import java.io.File

/** Tata letak folder publik Molina. Root dapat diganti (mis. folder sementara di tes). */
class MolinaPaths(val root: File = File(DEFAULT_ROOT)) {
    val logs: File get() = File(root, "logs")
    val terminal: File get() = File(root, "terminal")
    val cinema: File get() = File(root, "cinema")

    /** Urutan pembuatan: root lebih dulu. */
    val layout: List<File> get() = listOf(root, logs, terminal, cinema)

    companion object {
        const val DEFAULT_ROOT = "/storage/emulated/0/Molina"
    }
}
