package com.molina.studio

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Debug
import android.util.Log
import com.molina.core.common.CrashContext
import com.molina.core.common.StreamResolverRegistry
import com.molina.core.common.stream.CachingStreamResolver
import com.molina.core.common.stream.ChainStreamResolver
import com.molina.core.common.stream.DirectResolver
import com.molina.core.common.stream.GenericPageResolver
import com.molina.core.common.stream.HttpPageFetcher
import com.molina.core.common.stream.YtDlpResolver
import com.molina.core.security.UrlPolicy
import com.molina.engine.newpipe.NewPipeStreamResolver
import com.molina.core.storage.SevenZipBackend
import com.molina.log.CrashReporter
import com.molina.log.MolinaEvents
import com.molina.log.MolinaLogSetup

class MolinaApplication : Application() {
    /** Cache hasil resolve; dipangkas saat sistem kekurangan memori. */
    private var resolverCache: CachingStreamResolver? = null
    private var ytDlpWorker: DebianYtDlpWorker? = null // molina-wave3

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
        // molina-wave2: rantai resolver ringan -> berat, dibuat malas saat resolusi pertama.
        StreamResolverRegistry.install {
            val policy = { url: String -> UrlPolicy.check(url) is UrlPolicy.Verdict.Allowed }
            // molina-wave3: yt-dlp (TikTok/Facebook) lewat worker Python persisten di Debian; berkas worker ditulis saat ini.
            val ytDlp = DebianYtDlpWorker(this).also { w ->
                ytDlpWorker = w
                runCatching { w.ensureFiles() }
            }
            CachingStreamResolver(
                ChainStreamResolver(
                    listOf(
                        DirectResolver(),
                        NewPipeStreamResolver(),
                        YtDlpResolver(ytDlp, validate = policy),
                        GenericPageResolver(HttpPageFetcher(validate = policy), validate = policy),
                    ),
                    onEvent = { Log.d("Molina/resolver", it) },
                ),
            ).also { resolverCache = it }
        }
    }

    /** Mencatat pemakaian memori tiap sistem meminta pangkas, untuk menyetel tanpa adb. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            resolverCache?.clear()
            ytDlpWorker?.shutdown() // molina-wave3: proses Python di PRoot makan RAM; start ulang otomatis
        }
        val rt = Runtime.getRuntime()
        val javaMb = (rt.totalMemory() - rt.freeMemory()) shr 20
        val nativeMb = Debug.getNativeHeapAllocatedSize() shr 20
        MolinaEvents.behavior(
            "app", "trim",
            fields = mapOf("level" to level.toString(), "java_mb" to javaMb.toString(), "native_mb" to nativeMb.toString()),
        )
    }
}
