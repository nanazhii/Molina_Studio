package com.molina.studio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.molina.cinema.CinemaFragment
import com.molina.core.common.MolinaResult
import com.molina.core.storage.LocalMolinaStorage
import com.molina.files.FilesFragment
import com.molina.log.MolinaEvents
import com.molina.log.MolinaLog
import com.molina.log.MolinaLogSetup
import com.molina.log.MolinaLogger
import com.molina.settings.SettingsFragment
import com.molina.settings.TerminalPrefs
import com.molina.terminal.NanoKeysStateOwner
import com.molina.terminal.TerminalFragment
import com.molina.terminal.TerminalSessionFactory
import com.molina.terminal.RunMode
import com.molina.terminal.RunPrefsOwner
import com.molina.terminal.TerminalSessionFactoryOwner
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), TerminalSessionFactoryOwner, NanoKeysStateOwner, RunPrefsOwner {
    override val terminalSessionFactory: TerminalSessionFactory by lazy {
        createTerminalSessionFactory(applicationContext)
    }

    override var nanoKeysOpen: Boolean
        get() = TerminalPrefs.nanoKeysOpen(applicationContext)
        set(value) = TerminalPrefs.setNanoKeysOpen(applicationContext, value)

    override var runMode: RunMode
        get() = when (TerminalPrefs.runMode(applicationContext)) {
            TerminalPrefs.RUN_ACTIVE -> RunMode.ACTIVE
            TerminalPrefs.RUN_NEW -> RunMode.NEW
            else -> RunMode.ASK
        }
        set(value) = TerminalPrefs.setRunMode(
            applicationContext,
            when (value) {
                RunMode.ACTIVE -> TerminalPrefs.RUN_ACTIVE
                RunMode.NEW -> TerminalPrefs.RUN_NEW
                RunMode.ASK -> TerminalPrefs.RUN_ASK
            },
        )

    override val runLogEnabled: Boolean
        get() = TerminalPrefs.runLog(applicationContext)

    private val logTag = MolinaLogger.tag("core")
    private var currentTab: String? = null

    private val storagePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                onStorageReady(reinitLog = true)
            } else {
                MolinaLog.logger.w(logTag, "izin penyimpanan ditolak; log tetap di folder aplikasi")
            }
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(com.molina.core.ui.R.style.Theme_Molina_App)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        KeyboardNavHider.install(this, findViewById(R.id.nav_container))
        nav.setOnItemSelectedListener { item ->
            val tab = when (item.itemId) {
                R.id.nav_cinema -> "cinema"
                R.id.nav_files -> "files"
                R.id.nav_terminal -> "terminal"
                else -> "settings"
            }
            val fragment: Fragment = when (tab) {
                "cinema" -> CinemaFragment()
                "files" -> FilesFragment()
                "terminal" -> TerminalFragment()
                else -> SettingsFragment()
            }
            MolinaEvents.behavior("app", "nav", fields = mapOf("tab" to tab, "from" to (currentTab ?: "start")))
            currentTab = tab
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            supportFragmentManager.commit { replace(R.id.container, fragment) }
            true
        }
        if (savedInstanceState == null) {
            nav.selectedItemId = R.id.nav_cinema
            MolinaSessionService.start(this)
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (hasStoragePermission()) {
                onStorageReady(reinitLog = false)
            } else {
                storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun hasStoragePermission(): Boolean =
        checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** Folder /storage/emulated/0/Molina dibuat setelah izin ada; logger dipindah ke Molina/logs. */
    private fun onStorageReady(reinitLog: Boolean) {
        if (reinitLog) {
            MolinaLogSetup.init(applicationContext)
            BuildInfo.log(applicationContext)
        }
        lifecycleScope.launch {
            when (val r = LocalMolinaStorage().ensureLayout()) {
                is MolinaResult.Success -> MolinaLog.logger.i(logTag, "folder Molina siap")
                is MolinaResult.Failure -> MolinaLog.logger.e(logTag, "gagal menyiapkan folder Molina: ${r.error.message}")
            }
        }
    }
}
