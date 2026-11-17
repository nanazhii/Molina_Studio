package com.molina.cinema

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.molina.core.security.ShieldRegistry
import com.molina.core.security.UrlPolicy
import java.io.File

/** Hasil rencana peluncuran (murni, bisa diuji JVM). */
sealed interface LaunchPlan {
    data class Open(val uri: String) : LaunchPlan
    data object Empty : LaunchPlan
    data class Blocked(val message: String) : LaunchPlan
}

/**
 * Membuka pemutar (MPVActivity di :player-mpv) lewat Intent EKSPLISIT. Activity pemutar tidak diekspor,
 * jadi satu-satunya jalan masuk adalah dari aplikasi ini; kebijakan URL diperiksa di sini dan sekali lagi di pemutar.
 * Nama kelas berupa string agar :cinema tidak bergantung pada :player-mpv.
 */
object PlayerLauncher {
    const val ACTIVITY_CLASS = "com.molina.player.MPVActivity"
    /** Harus sama dengan MPVActivity.EXTRA_AUDIO_ONLY. */
    const val EXTRA_AUDIO_ONLY = "com.molina.player.extra.AUDIO_ONLY"

    fun plan(input: String, allowLan: Boolean): LaunchPlan {
        val u = input.trim()
        if (u.isEmpty()) return LaunchPlan.Empty
        val v = UrlPolicy.check(u, allowLan)
        if (v is UrlPolicy.Verdict.Blocked) return LaunchPlan.Blocked(v.reason.message)
        return LaunchPlan.Open(u)
    }

    fun open(context: Context, input: String, audioOnly: Boolean): LaunchPlan {
        val allowLan = ShieldRegistry.current()?.config()?.allowLan ?: false
        val p = plan(input, allowLan)
        if (p !is LaunchPlan.Open) return p
        val uri = if (p.uri.startsWith("/")) Uri.fromFile(File(p.uri)) else Uri.parse(p.uri)
        val i = Intent(Intent.ACTION_VIEW, uri)
            .setClassName(context.packageName, ACTIVITY_CLASS)
            .putExtra(EXTRA_AUDIO_ONLY, audioOnly)
        if (context !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(i)
            p
        } catch (e: ActivityNotFoundException) {
            LaunchPlan.Blocked("pemutar tidak tersedia di build ini")
        }
    }
}
