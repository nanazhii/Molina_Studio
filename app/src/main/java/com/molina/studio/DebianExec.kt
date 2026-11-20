// molina-ytdlp-install
package com.molina.studio

import android.content.Context
import android.os.Build
import com.molina.core.common.SevenZipCommand
import com.molina.terminal.proot.DebianLayout
import com.molina.terminal.proot.ProotCompat
import com.molina.terminal.proot.ProotExec
import java.io.File

/** Satu tempat untuk merakit proses PRoot tanpa shell (dipakai worker dan pemasang yt-dlp). */
internal object DebianExec {
    /** ProcessBuilder yang menjalankan [binary] di dalam Debian, atau null bila PRoot belum siap. stderr dibuang. */
    fun builder(context: Context, binary: String, args: List<String>): ProcessBuilder? {
        val l = DebianLayout(context.filesDir)
        if (!l.isProotReady()) return null
        val cmd = SevenZipCommand.prootArgsScoped(l.rootfsDir.path, emptyList(), binary, args)
        val pb = ProcessBuilder(listOf(l.prootBin.path) + cmd)
            .directory(context.filesDir)
            // stderr dibuang (bisa memuat URL). Redirect.DISCARD tidak tersedia di semua API Android, jadi pakai /dev/null.
            .redirectError(File("/dev/null"))
        pb.environment().apply {
            clear()
            putAll(
                ProotExec.env(
                    l, context.filesDir.absolutePath,
                    noSeccomp = Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1, compat = ProotCompat.enabled,
                ),
            )
        }
        return pb
    }
}
