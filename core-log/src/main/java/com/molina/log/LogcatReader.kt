package com.molina.log

import android.content.Context
import android.content.pm.PackageManager
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/** Opsi setara adb logcat. */
data class LogcatOptions(
    val format: String = "threadtime",           // -v
    val buffers: List<String> = emptyList(),     // -b main|system|crash|events|radio|all
    val filters: List<String> = emptyList(),     // mis. "Molina/term:D", "*:S"
    val dump: Boolean = false,                   // -d (cetak lalu selesai)
    val tail: Int? = null,                       // -t N (N baris terakhir, lalu selesai)
    val pid: Int? = null,                        // --pid=N
)

object LogcatReader {
    private const val READ_LOGS = "android.permission.READ_LOGS"

    fun buildCommand(o: LogcatOptions): List<String> = buildList {
        add("logcat"); add("-v"); add(o.format)
        o.buffers.forEach { add("-b"); add(it) }
        if (o.tail != null) { add("-t"); add(o.tail.toString()) } else if (o.dump) add("-d")
        o.pid?.let { add("--pid=$it") }
        addAll(o.filters)
    }

    /** true = logcat penuh seperti adb; false = hanya log proses aplikasi ini. */
    fun hasFullAccess(context: Context): Boolean =
        context.checkSelfPermission(READ_LOGS) == PackageManager.PERMISSION_GRANTED

    /** Alirkan baris logcat apa adanya. Batalkan koleksi untuk menghentikan proses. */
    fun stream(options: LogcatOptions = LogcatOptions()): Flow<String> = callbackFlow {
        val proc = try {
            ProcessBuilder(buildCommand(options)).redirectErrorStream(true).start()
        } catch (e: IOException) {
            close(e)
            return@callbackFlow
        }
        val reader = launch(Dispatchers.IO) {
            try {
                proc.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) send(line)
                }
            } catch (_: IOException) {
            }
            close()
        }
        awaitClose {
            reader.cancel()
            proc.destroy()
        }
    }
}
