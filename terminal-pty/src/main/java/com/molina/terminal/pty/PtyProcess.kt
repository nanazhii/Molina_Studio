package com.molina.terminal.pty

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.Closeable
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Subproses yang terhubung ke pseudo-terminal (PTY).
 *
 * [input] membaca keluaran proses, [output] menulis masukan ke proses. [waitFor] memblokir,
 * jadi panggil dari thread latar (bukan main thread). [close] menutup sisi master PTY dan
 * aman dipanggil berulang.
 */
class PtyProcess private constructor(
    val pid: Int,
    private val master: ParcelFileDescriptor,
) : Closeable {

    private val closed = AtomicBoolean(false)

    val input: FileInputStream = FileInputStream(master.fileDescriptor)
    val output: FileOutputStream = FileOutputStream(master.fileDescriptor)

    /** Beri tahu proses bahwa ukuran jendela terminal berubah. Diabaikan bila sudah ditutup. */
    fun resize(rows: Int, columns: Int, cellWidth: Int = 0, cellHeight: Int = 0) {
        require(rows > 0 && columns > 0) { "rows dan columns harus > 0" }
        if (!closed.get()) PtyNative.setPtyWindowSize(master.fd, rows, columns, cellWidth, cellHeight)
    }

    /** Blokir sampai proses selesai. Mengembalikan kode keluar, atau -sinyal bila dihentikan sinyal. */
    fun waitFor(): Int = PtyNative.waitFor(pid)

    /** Kirim sinyal ke proses (bawaan SIGKILL). Mengembalikan false bila gagal (mis. proses sudah selesai). */
    fun kill(signal: Int = OsConstants.SIGKILL): Boolean =
        try {
            Os.kill(pid, signal)
            true
        } catch (_: ErrnoException) {
            false
        }

    override fun close() {
        if (closed.compareAndSet(false, true)) master.close()
    }

    companion object {
        /**
         * Jalankan [command] di PTY baru.
         *
         * @param args argv lengkap termasuk argv[0]; bila kosong dipakai [command] sebagai argv[0]
         * @param env daftar `KEY=VALUE`; lingkungan proses induk TIDAK diwariskan
         */
        @JvmStatic
        fun spawn(
            command: String,
            cwd: String,
            args: Array<String> = emptyArray(),
            env: Array<String> = emptyArray(),
            rows: Int = 24,
            columns: Int = 80,
            cellWidth: Int = 0,
            cellHeight: Int = 0,
        ): PtyProcess {
            require(command.isNotEmpty()) { "command kosong" }
            require(rows > 0 && columns > 0) { "rows dan columns harus > 0" }
            val argv = if (args.isEmpty()) arrayOf(command) else args
            val pidOut = IntArray(1)
            val fd = PtyNative.createSubprocess(command, cwd, argv, env, pidOut, rows, columns, cellWidth, cellHeight)
            return PtyProcess(pidOut[0], ParcelFileDescriptor.adoptFd(fd))
        }
    }
}
