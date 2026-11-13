// molina-patch17
package com.molina.studio

import android.os.Process
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * Mematikan semua proses lain milik UID aplikasi ini (anak proses PTY, proot, tracee, daemon
 * yang lepas dari sesi). Tanpa celah fork: tiap putaran membekukan (SIGSTOP) semua proses dulu,
 * baru SIGKILL, lalu memindai ulang sampai kosong. Zombie tidak dihitung; mereka dibersihkan
 * kernel (diwariskan ke init) begitu proses aplikasi ini mati.
 */
internal object ProcessReaper {
    class Result(val killed: Int, val leftover: Int)

    fun killOthers(maxRounds: Int = 5): Result {
        val killed = HashSet<Int>()
        for (round in 1..maxRounds) {
            val alive = aliveOthers()
            if (alive.isEmpty()) return Result(killed.size, 0)
            alive.forEach { signal(it, OsConstants.SIGSTOP) }
            alive.forEach { if (signal(it, OsConstants.SIGKILL)) killed.add(it) }
            Thread.sleep(25L * round)
        }
        return Result(killed.size, aliveOthers().size)
    }

    /** Karakter status dari isi /proc/<pid>/stat (setelah tanda kurung terakhir); null bila tak terbaca. */
    internal fun parseState(stat: String): Char? {
        val i = stat.lastIndexOf(')')
        return if (i < 0 || i + 2 >= stat.length) null else stat[i + 2]
    }

    private fun aliveOthers(): List<Int> {
        val me = Process.myPid()
        val uid = Process.myUid()
        val out = ArrayList<Int>()
        File("/proc").listFiles()?.forEach { dir ->
            val pid = dir.name.toIntOrNull() ?: return@forEach
            if (pid == me) return@forEach
            try {
                if (Os.stat(dir.path).st_uid != uid) return@forEach
                val state = parseState(File(dir, "stat").readText())
                if (state != null && state != 'Z' && state != 'X') out.add(pid)
            } catch (_: Exception) {
                // proses sudah hilang atau tidak terbaca
            }
        }
        return out
    }

    private fun signal(pid: Int, sig: Int): Boolean =
        try {
            Os.kill(pid, sig)
            true
        } catch (_: Exception) {
            false
        }
}
