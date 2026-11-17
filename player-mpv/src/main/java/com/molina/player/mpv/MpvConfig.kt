// molina-patch-a
package com.molina.player.mpv

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.IOException

/**
 * Menyiapkan config-dir mpv di filesDir/mpv: cacert.pem (verifikasi TLS) dan fonts.conf (fontconfig
 * untuk libass). Tanpa fonts.conf libass tidak menemukan font sistem Android; tanpa cacert.pem HTTPS
 * gagal verifikasi (tls-verify=yes, gagal tertutup).
 * Pola mengikuti Utils.copyAssets pada mpv-android (MIT) commit ebf1b3f0cbe9, ditulis ulang. // gate:allow atribusi
 */
internal object MpvConfig {
    const val DIR_NAME = "mpv"
    const val CACERT = "cacert.pem"
    const val FONTS_CONF = "fonts.conf"

    class Prepared(val dir: File, val certOk: Boolean, val fontsOk: Boolean) {
        val caFile: File get() = File(dir, CACERT)
    }

    /** Aman dipanggil berulang; tidak melempar IOException (kegagalan dilaporkan lewat flag). */
    fun prepare(context: Context): Prepared {
        val dir = File(context.filesDir, DIR_NAME)
        val dirOk = dir.isDirectory || dir.mkdirs()
        val certOk = dirOk && try {
            val bytes = context.assets.open(CACERT, AssetManager.ACCESS_STREAMING).use { it.readBytes() }
            bytes.isNotEmpty() && writeIfChanged(File(dir, CACERT), bytes)
        } catch (_: IOException) {
            false
        }
        val conf = fontsConf(context.cacheDir.path).toByteArray(Charsets.UTF_8)
        val fontsOk = dirOk && writeIfChanged(File(dir, FONTS_CONF), conf)
        return Prepared(dir, certOk, fontsOk)
    }

    /** true bila berkas kini berisi [bytes]. Tidak menulis ulang bila sudah sama; penulisan atomik lewat berkas sementara. */
    internal fun writeIfChanged(target: File, bytes: ByteArray): Boolean {
        return try {
            if (target.isFile && target.length() == bytes.size.toLong() && target.readBytes().contentEquals(bytes)) {
                true
            } else {
                val tmp = File(target.parentFile, target.name + ".tmp")
                tmp.writeBytes(bytes)
                if (tmp.renameTo(target)) {
                    true
                } else {
                    tmp.delete()
                    false
                }
            }
        } catch (_: IOException) {
            false
        }
    }

    /** Konfigurasi fontconfig untuk Android: font sistem + alias keluarga umum (Android tidak menyediakan /etc/fonts). */
    internal fun fontsConf(cacheDir: String): String = listOf(
        "<fontconfig>",
        "<dir>/system/fonts/</dir>",
        "<dir>/product/fonts/</dir>",
        "<cachedir>${xmlEscape(cacheDir)}</cachedir>",
        "<alias><family>serif</family>",
        "<prefer><family>Noto Serif</family></prefer>",
        "</alias>",
        "<alias><family>sans-serif</family>",
        "<prefer>",
        "<family>Roboto</family>",
        "<family>Noto Sans</family>",
        "</prefer>",
        "</alias>",
        "<alias><family>monospace</family>",
        "<prefer><family>Droid Sans Mono</family></prefer>",
        "</alias>",
        "</fontconfig>",
    ).joinToString("\n")

    internal fun xmlEscape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
}
