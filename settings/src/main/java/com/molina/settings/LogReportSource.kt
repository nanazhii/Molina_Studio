package com.molina.settings

import android.content.Context
import com.molina.log.MolinaLogSetup
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** Membaca ekor molina.log (dan hasil rotasi terakhir) lalu menyusunnya menjadi laporan masalah. */
internal object LogReportSource {
    private const val TAIL_BYTES = 256 * 1024L

    fun create(ctx: Context): String {
        val buildLine = BuildLine.describe(ctx)
        val current = listOfNotNull(
            File(MolinaLogSetup.PUBLIC_DIR),
            ctx.getExternalFilesDir("logs"),
            File(ctx.filesDir, "logs"),
        ).map { File(it, "molina.log") }
            .filter { it.isFile }
            .maxByOrNull { it.lastModified() }
            ?: return buildLine + "\n\n(molina.log belum ditemukan)\n"
        return try {
            val rotated = File(current.parentFile, "molina.1.log")
            val entries = (if (rotated.isFile) LogReport.parse(readTail(rotated)) else emptyList()) +
                LogReport.parse(readTail(current))
            LogReport.build(buildLine, entries)
        } catch (e: IOException) {
            buildLine + "\n\n(gagal membaca log: " + e.message + ")\n"
        }
    }

    private fun readTail(file: File): String = RandomAccessFile(file, "r").use { raf ->
        val len = raf.length()
        val start = (len - TAIL_BYTES).coerceAtLeast(0L)
        raf.seek(start)
        val buf = ByteArray((len - start).toInt())
        raf.readFully(buf)
        String(buf, Charsets.UTF_8)
    }
}
