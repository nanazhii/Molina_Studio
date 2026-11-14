// molina-patch26e1
package com.molina.core.storage

/** Penyimpanan kunci-nilai kecil. Antarmuka murni agar logika Favorit/Terakhir/Prefs bisa dites di JVM. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
}

/** Implementasi di memori (untuk tes). */
class MemoryKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, Any>()
    override fun getString(key: String): String? = map[key] as? String
    override fun putString(key: String, value: String) { map[key] = value }
    override fun getBoolean(key: String, default: Boolean): Boolean = map[key] as? Boolean ?: default
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
}
