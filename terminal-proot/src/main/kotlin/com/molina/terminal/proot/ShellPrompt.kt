// molina-patch15
package com.molina.terminal.proot

import com.molina.log.MolinaEvents
import java.io.File

/** Prompt bash Debian: hijau di home dan /sdcard/Molina, merah di tempat lain. Hanya tampilan; uid guest tidak berubah. */
object ShellPrompt {
    const val SCRIPT_PATH = "etc/profile.d/molina-prompt.sh"
    const val ENV_PATH = "etc/molina/prompt.env"
    private const val RESOURCE = "/molina/prompt.sh"

    fun envText(showRoot: Boolean): String =
        "MOLINA_PS_NAME='${if (showRoot) "root" else "user"}'\nMOLINA_PS_OK='1;32'\nMOLINA_PS_BAD='1;31'\n"

    fun script(): String =
        ShellPrompt::class.java.getResourceAsStream(RESOURCE)?.bufferedReader()?.use { it.readText() }
            ?: error("resource $RESOURCE tidak ditemukan")

    /** Menulis hanya bila isi berbeda. Tidak pernah melempar: kegagalan dicatat sebagai BUG dan sesi lanjut. */
    fun install(rootfs: File, showRoot: Boolean, id: String? = null) {
        try {
            writeIfDifferent(File(rootfs, SCRIPT_PATH), script())
            writeIfDifferent(File(rootfs, ENV_PATH), envText(showRoot))
        } catch (e: Exception) {
            MolinaEvents.bug("proot", "prompt-failed", e.message ?: e.javaClass.simpleName, e, id = id)
        }
    }

    private fun writeIfDifferent(f: File, text: String) {
        if (f.isFile && f.readText() == text) return
        f.parentFile?.mkdirs()
        f.writeText(text)
        f.setReadable(true, false)
    }
}
