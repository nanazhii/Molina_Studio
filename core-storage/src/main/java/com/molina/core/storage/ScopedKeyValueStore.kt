// molina-patch26e2
package com.molina.core.storage

/**
 * Membungkus [inner] dengan awalan [scope] pada setiap kunci. Dipakai agar riwayat path relatif (Terakhir,
 * Favorit) terikat ke root yang sedang dipakai: bila root berubah, data root lama tidak ikut terbaca.
 */
class ScopedKeyValueStore(private val inner: KeyValueStore, scope: String) : KeyValueStore {
    private val prefix = "$scope|"
    override fun getString(key: String): String? = inner.getString(prefix + key)
    override fun putString(key: String, value: String) = inner.putString(prefix + key, value)
    override fun getBoolean(key: String, default: Boolean): Boolean = inner.getBoolean(prefix + key, default)
    override fun putBoolean(key: String, value: Boolean) = inner.putBoolean(prefix + key, value)
}
