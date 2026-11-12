package com.molina.terminal

import android.app.Application
import com.molina.log.MolinaLogSetup

class MolinaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MolinaLogSetup.init(this)
    }
}
