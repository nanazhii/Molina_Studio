package com.molina.terminal.core

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

/**
 * Sisi master sebuah proses yang berjalan di PTY. Dipisah dari JNI agar [PtyTerminalSession]
 * bisa diuji di JVM tanpa pustaka native.
 */
interface PtyChannel : Closeable {
    val pid: Int

    /** Keluaran proses. `read` memblokir; berakhir (EOF atau IOException) saat proses selesai/ditutup. */
    val input: InputStream

    /** Masukan ke proses. */
    val output: OutputStream

    fun resize(rows: Int, columns: Int, cellWidth: Int, cellHeight: Int)

    /** Blokir sampai proses selesai. Kode keluar, atau -sinyal bila dihentikan sinyal. */
    fun waitFor(): Int

    /** Kirim SIGKILL. Aman dipanggil pada proses yang sudah selesai. */
    fun kill()
}

/** Pembuat [PtyChannel]. Bawaan: [DefaultPtyLauncher] (JNI). */
fun interface PtyLauncher {
    fun launch(config: SessionConfig, rows: Int, columns: Int, cellWidth: Int, cellHeight: Int): PtyChannel
}
