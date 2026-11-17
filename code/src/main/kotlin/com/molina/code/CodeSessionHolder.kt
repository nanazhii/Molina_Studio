package com.molina.code

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.molina.code.ui.CodeSession
import com.molina.code.ui.RunStore
import com.molina.core.common.MolinaPaths
import java.io.File

/** ViewModel Activity: sesi editor (tab terbuka, draf, riwayat) bertahan saat pindah tab dan rotasi layar. */
class CodeSessionHolder(app: Application) : AndroidViewModel(app) {
    val session: CodeSession = CodeSession(MolinaPaths().root.path, File(app.filesDir, "code-drafts")).also { s ->
        val prefs = app.getSharedPreferences("molina_code_run", Context.MODE_PRIVATE)
        s.runStore = object : RunStore {
            override fun get(key: String): String? = prefs.getString(key, null)
            override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
        }
    }

    override fun onCleared() {
        session.releaseAll()
    }
}
