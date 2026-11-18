package com.molina.studio

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Debug
import com.molina.core.common.CrashContext
import com.molina.core.storage.SevenZipBackend
import com.molina.log.CrashReporter
import com.molina.log.MolinaEvents
import com.molina.log.MolinaLogSetup

class MolinaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MolinaLogSetup.init(this)
        CrashReporter.install(context = { CrashContext.describe() })
        BuildInfo.log(this)
        // Alias ikon alternatif sudah dihapus: kembalikan alias peluncur ke status bawaan manifest.
        packageManager.setComponentEnabledSetting(
            ComponentName(this, "com.molina.studio.IconGreen"),
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP,
        )
        ShieldBootstrap.start(this)
        SevenZipBackend.writer = DebianSevenZip(this) // molina-27q: 7z bersandi lewat Debian
    }

    /** Mencatat pemakaian memori tiap sistem meminta pangkas, untuk menyetel tanpa adb. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val rt = Runtime.getRuntime()
        val javaMb = (rt.totalMemory() - rt.freeMemory()) shr 20
        val nativeMb = Debug.getNativeHeapAllocatedSize() shr 20
        MolinaEvents.behavior(
            "app", "trim",
            fields = mapOf("level" to level.toString(), "java_mb" to javaMb.toString(), "native_mb" to nativeMb.toString()),
        )
    }
}
