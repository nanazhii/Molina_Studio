// molina-patch10b
package com.molina.studio

import android.content.Context
import android.os.Build
import com.molina.log.MolinaEvents
import com.molina.terminal.TerminalSessionFactory
import com.molina.terminal.core.PtyTerminalSession
import com.molina.terminal.core.SessionConfig
import com.molina.terminal.core.TerminalSessionIds
import com.molina.terminal.proot.DebianLayout
import com.molina.terminal.proot.ProotCompat
import com.molina.terminal.proot.ProotLauncher
import java.io.File

private const val SHELL = "/system/bin/sh"
private const val MOLINA_DIR = "/storage/emulated/0/Molina"

/** Alasan jatuh ke shell Android; ikut dicatat di term.session.start agar tidak menebak. */
private fun fallbackReason(layout: DebianLayout, prepareFailed: Boolean): String = when {
    prepareFailed -> "prepare-failed"
    !layout.isRootfsInstalled() -> "rootfs-missing"
    !layout.isProotReady() -> "proot-not-ready"
    else -> "unknown"
}

/**
 * Pabrik sesi: Debian PRoot bila rootfs dan proot siap, selain itu shell Android.
 * Keputusan diambil tiap sesi dibuat, jadi tab Terminal tidak perlu tahu apa pun soal Debian.
 */
internal fun createTerminalSessionFactory(context: Context): TerminalSessionFactory {
    val app = context.applicationContext
    return TerminalSessionFactory { client ->
        val sessionId = TerminalSessionIds.next()
        val home = app.filesDir.absolutePath
        val layout = DebianLayout(app.filesDir)
        var prepareFailed = false
        val debian = if (layout.isReady()) {
            try {
                ProotLauncher.prepareGuest(layout.rootfsDir, id = sessionId)
                ProotLauncher.build(layout, home, noSeccomp = Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1, id = sessionId, compat = ProotCompat.enabled)
            } catch (e: Exception) {
                prepareFailed = true
                MolinaEvents.bug("proot", "prepare-failed", e.message ?: e.javaClass.simpleName, e, id = sessionId)
                null
            }
        } else {
            null
        }
        val config = if (debian != null) {
            SessionConfig(command = debian.command, workingDir = debian.workingDir, env = debian.env)
        } else {
            val cwd = File(MOLINA_DIR).takeIf { it.isDirectory }?.absolutePath ?: home
            SessionConfig(
                command = listOf(SHELL),
                workingDir = cwd,
                env = mapOf(
                    "HOME" to home,
                    "TMPDIR" to app.cacheDir.absolutePath,
                    "SHELL" to SHELL,
                    "PATH" to "/system/bin:/system/xbin",
                    "LANG" to "en_US.UTF-8",
                ),
            )
        }
        val startFields = linkedMapOf("kind" to if (debian != null) "debian" else "android-shell")
        if (debian == null) startFields["why"] = fallbackReason(layout, prepareFailed)
        MolinaEvents.behavior("term", "session.start", fields = startFields, id = sessionId)
        PtyTerminalSession(config = config, client = client, sessionId = sessionId)
    }
}
