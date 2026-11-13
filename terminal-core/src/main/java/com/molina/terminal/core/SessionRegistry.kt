// molina-patch16
package com.molina.terminal.core

import java.util.Collections
import java.util.WeakHashMap

/**
 * Daftar sesi terminal yang hidup, supaya aplikasi bisa menutup semuanya sekaligus (aksi Keluar
 * di notifikasi). Referensi lemah: sesi yang sudah dibuang tidak tertahan di sini.
 */
object SessionRegistry {
    private val sessions: MutableSet<AutoCloseable> =
        Collections.newSetFromMap(WeakHashMap<AutoCloseable, Boolean>())

    fun add(session: AutoCloseable) {
        synchronized(sessions) { sessions.add(session) }
    }

    fun remove(session: AutoCloseable) {
        synchronized(sessions) { sessions.remove(session) }
    }

    /** Menutup semua sesi terdaftar (close aman dipanggil berulang). Mengembalikan jumlah yang ditutup. */
    fun closeAll(): Int {
        val snapshot = synchronized(sessions) { sessions.toList().also { sessions.clear() } }
        snapshot.forEach { runCatching { it.close() } }
        return snapshot.size
    }
}
