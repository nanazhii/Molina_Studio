package com.molina.player.preferences

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import com.molina.core.common.diag.Diag
import com.molina.player.BuildConfig
import com.molina.player.MPVLib
import com.molina.player.MPVLib.MpvLogLevel
import com.molina.player.R
import com.molina.player.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity(), MPVLib.LogObserver {
    private lateinit var binding: ActivityAboutBinding
    private var logs = ""
    private var mpvDestroyed = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        if (preferences.getBoolean("material_you_theming", false))
            DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.elevation = 0f
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        logs = "Molina Player ${BuildConfig.VERSION_NAME} / ${BuildConfig.VERSION_CODE} (${BuildConfig.BUILD_TYPE})\n"

        // create mpv context to capture version info from log
        try {
            MPVLib.create(this)
            mpvDestroyed = false
            MPVLib.addLogObserver(this)
            MPVLib.init()
        } catch (e: Exception) {
            failedToStartMpv(e)
        } catch (e: LinkageError) {
            failedToStartMpv(e)
        }
    }

    /** Halaman About tetap terbuka walau libmpv gagal dibuat; versi aplikasi tetap tampil, galat dicatat. */
    private fun failedToStartMpv(e: Throwable) {
        Diag.bug("player", "about-mpv-failed", "mpv gagal dibuat untuk halaman About", e)
        if (!mpvDestroyed) {
            mpvDestroyed = true
            try { MPVLib.destroy() } catch (_: Exception) { }
        }
        logs += "libmpv tidak tersedia: ${e.javaClass.simpleName}\n"
        updateLog()
    }

    private fun updateLog() {
        runOnUiThread {
            binding.logs.text = logs
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!mpvDestroyed) {
            MPVLib.destroy()
            mpvDestroyed = true
        }
    }

    override fun logMessage(prefix: String, level: Int, text: String) {
        if (prefix != "cplayer")
            return
        if (level == MpvLogLevel.MPV_LOG_LEVEL_V)
            logs += text

        if (text.startsWith("List of enabled features:", true)) {
            // stop receiving log messages and populate text field
            MPVLib.removeLogObserver(this)
            updateLog()
        }
    }
}
