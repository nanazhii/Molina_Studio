// molina-patch14
package com.molina.terminal.proot

/** Mode kompatibilitas proot untuk sesi berikutnya (tanpa seccomp, tanpa --sysvipc). Hanya di memori. */
object ProotCompat {
    @Volatile
    var enabled: Boolean = false
}
