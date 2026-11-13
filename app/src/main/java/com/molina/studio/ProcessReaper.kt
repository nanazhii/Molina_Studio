// molina-patch16
package com.molina.studio

import android.os.Process
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * Mematikan semua proses lain milik UID aplikasi ini (anak proses PTY, proot, tracee, daemon
 * yang lepas dari sesi). Membaca /proc; pemilik direktori /proc/<pid> adalah UID prosesnya.
 */
internal object ProcessReaper {
    fun killOthers(): Int {
        val me = Process.myPid()
        val uid = Process.myUid()
        var killed = 0
        File("/proc").listFiles()?.forEach { dir ->
            val pid = dir.name.toIntOrNull() ?: return@forEach
            if (pid == me) return@forEach
            try {
                if (Os.stat(dir.path).st_uid == uid) {
                    Os.kill(pid, OsConstants.SIGKILL)
                    killed++
                }
            } catch (_: Exception) {
                // proses sudah hilang atau tidak terbaca
            }
        }
        return killed
    }
}
