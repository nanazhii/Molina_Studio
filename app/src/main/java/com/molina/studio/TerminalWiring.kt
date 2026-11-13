// molina-patch10b
package com.molina.studio

import android.content.Context
import com.molina.terminal.TerminalSessionFactory
import com.molina.terminal.core.PtyTerminalSession
import com.molina.terminal.core.SessionConfig
import com.molina.terminal.proot.DebianLayout
import com.molina.terminal.proot.ProotLauncher
import java.io.File

private const val SHELL = "/system/bin/sh"
private const val MOLINA_DIR = "/storage/emulated/0/Molina"

/**
 * Pabrik sesi: Debian PRoot bila rootfs dan proot siap, selain itu shell Android.
 * Keputusan diambil tiap sesi dibuat, jadi tab Terminal tidak perlu tahu apa pun soal Debian.
 */
internal fun createTerminalSessionFactory(context: Context): TerminalSessionFactory {
    val app = context.applicationContext
    return TerminalSessionFactory { client ->
        val home = app.filesDir.absolutePath
        val layout = DebianLayout(app.filesDir)
        val debian = if (layout.isReady()) {
            try {
                ProotLauncher.prepareGuest(layout.rootfsDir)
                ProotLauncher.build(layout, home)
            } catch (e: Exception) {
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
        PtyTerminalSession(config = config, client = client)
    }
}
