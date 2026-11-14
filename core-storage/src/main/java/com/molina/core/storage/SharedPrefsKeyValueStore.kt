// molina-patch26e1
package com.molina.core.storage

import android.content.Context

/** KeyValueStore di atas SharedPreferences (bawaan "molina_explorer", sama dengan repo lama). */
class SharedPrefsKeyValueStore(context: Context, name: String = "molina_explorer") : KeyValueStore {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
}
