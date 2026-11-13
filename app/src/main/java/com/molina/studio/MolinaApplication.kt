package com.molina.studio

import android.app.Application
import android.os.Debug
import com.molina.log.CrashReporter
import com.molina.log.MolinaEvents
import com.molina.log.MolinaLogSetup

class MolinaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MolinaLogSetup.init(this)
        CrashReporter.install()
        BuildInfo.log(this)
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
