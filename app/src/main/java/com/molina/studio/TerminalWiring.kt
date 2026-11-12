// molina-patch8
package com.molina.studio

import android.content.Context
import com.molina.terminal.TerminalSessionFactory
import com.molina.terminal.core.PtyTerminalSession
import com.molina.terminal.core.SessionConfig
import java.io.File

private const val SHELL = "/system/bin/sh"
private const val MOLINA_DIR = "/storage/emulated/0/Molina"

/**
 * Pabrik sesi bawaan: shell Android. Patch berikutnya menggantinya dengan Debian PRoot
 * (`terminal-proot`) tanpa menyentuh fitur terminal.
 */
internal fun createTerminalSessionFactory(context: Context): TerminalSessionFactory {
    val app = context.applicationContext
    return TerminalSessionFactory { client ->
        val home = app.filesDir.absolutePath
        val cwd = File(MOLINA_DIR).takeIf { it.isDirectory }?.absolutePath ?: home
        PtyTerminalSession(
            config = SessionConfig(
                command = listOf(SHELL),
                workingDir = cwd,
                env = mapOf(
                    "HOME" to home,
                    "TMPDIR" to app.cacheDir.absolutePath,
                    "SHELL" to SHELL,
                    "PATH" to "/system/bin:/system/xbin",
                    "LANG" to "en_US.UTF-8",
                ),
            ),
            client = client,
        )
    }
}
