// molina-27m-fix
package com.molina.files

/**
 * [molina-27m-fix] Dipasang oleh Activity induk (MainActivity). Berkas memintanya memutar media di Molina Cinema
 * tanpa bergantung ke modul Cinema/player-api (aturan module_gate: fitur tidak saling bergantung).
 */
interface MediaPlayerHost {
    /** Pindah ke Cinema dan putar [path]. false bila tidak bisa. Dipanggil dari main thread. */
    fun playMedia(path: String): Boolean
}
