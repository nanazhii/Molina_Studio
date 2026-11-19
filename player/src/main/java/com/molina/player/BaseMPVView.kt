package com.molina.player

import android.content.Context
import android.util.AttributeSet
import android.os.SystemClock
import com.molina.core.common.diag.Diag
import com.molina.player.mpv.MpvGateway
import com.molina.player.mpv.MpvProperties
import android.view.SurfaceHolder
import android.view.SurfaceView

// Contains only the essential code needed to get a picture on the screen

abstract class BaseMPVView(context: Context, attrs: AttributeSet) : SurfaceView(context, attrs), SurfaceHolder.Callback {
    /**
     * Initialize libmpv.
     *
     * Call this once before the view is shown.
     */
    fun initialize(configDir: String, cacheDir: String) {
        val t0 = SystemClock.elapsedRealtime()
        MPVLib.create(context.applicationContext)

        /* set normal options (user-supplied config can override) */
        MpvProperties.setOption("config", "yes")
        MpvProperties.setOption("config-dir", configDir)
        for (opt in arrayOf("gpu-shader-cache-dir", "icc-cache-dir"))
            MpvProperties.setOption(opt, cacheDir)
        initOptions()

        MPVLib.init()

        /* set hardcoded options */
        postInitOptions()
        // could mess up VO init before surfaceCreated() is called
        MpvProperties.setOption("force-window", "no")
        // need to idle at least once for playFile() logic to work
        MpvProperties.setOption("idle", "once")

        holder.addCallback(this)
        observeProperties()
        Diag.behavior("mpv", "initialized", "libmpv siap", mapOf("ms" to (SystemClock.elapsedRealtime() - t0).toString()))
    }

    /**
     * Deinitialize libmpv.
     *
     * Call this once before the view is destroyed.
     */
    fun destroy() {
        // Disable surface callbacks to avoid using uninitialized mpv state
        holder.removeCallback(this)

        MPVLib.destroy()
    }

    protected abstract fun initOptions()
    protected abstract fun postInitOptions()

    protected abstract fun observeProperties()

    // molina-resolver: pemuatan awal bisa datang dari thread lain (setelah resolusi URL), jadi
    // status surface dan muatan tertunda dijaga satu kunci agar tidak ada muatan yang hilang.
    private val loadLock = Any()
    private var surfaceAttached = false
    private var pendingLoad: Array<String>? = null

    /**
     * Set the first file to be played once the player is ready.
     */
    fun playFile(filePath: String) = loadArgs(arrayOf("loadfile", filePath, "replace"))

    /**
     * Jalankan perintah `loadfile` sekarang bila surface sudah terpasang, jika belum tunda sampai
     * surfaceCreated(). Aman dipanggil dari thread mana pun, tetapi JNI bisa memblokir: panggil
     * dari thread mpv khusus, bukan main thread.
     */
    fun loadArgs(args: Array<String>) {
        val now = synchronized(loadLock) {
            if (surfaceAttached) {
                true
            } else {
                pendingLoad = args
                false
            }
        }
        if (now) MpvGateway.command(args)
    }

    private var voInUse: String = "gpu"

    /**
     * Sets the VO to use.
     * It is automatically disabled/enabled when the surface dis-/appears.
     */
    fun setVo(vo: String) {
        voInUse = vo
        MpvProperties.setOption("vo", vo)
    }

    // Surface callbacks

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        MpvProperties.setString("android-surface-size", "${width}x$height")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Diag.behavior("mpv", "surface-attached", "attach surface")
        MPVLib.attachSurface(holder.surface)
        // This forces mpv to render subs/osd/whatever into our surface even if it would ordinarily not
        MpvProperties.setOption("force-window", "yes")

        val pending = synchronized(loadLock) {
            surfaceAttached = true
            pendingLoad.also { pendingLoad = null }
        }
        if (pending != null) {
            MpvGateway.command(pending)
        } else {
            // We disable video output when the context disappears, enable it back
            MpvProperties.setString("vo", voInUse)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Diag.behavior("mpv", "surface-detached", "detach surface")
        synchronized(loadLock) { surfaceAttached = false }
        MpvProperties.setString("vo", "null")
        MpvProperties.setString("force-window", "no")
        // Note that before calling detachSurface() we need to be sure that libmpv
        // is done using the surface.
        // FIXME: There could be a race condition here, because I don't think
        // setting a property will wait for VO deinit.
        MPVLib.detachSurface()
    }

}
