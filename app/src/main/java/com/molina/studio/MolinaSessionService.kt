// molina-patch16
package com.molina.studio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import com.molina.log.MolinaEvents
import kotlin.concurrent.thread

/**
 * Layanan latar dengan notifikasi tetap: menjaga sesi terminal tetap hidup saat aplikasi di
 * latar, dan menyediakan aksi Keluar yang mematikan semua proses aplikasi.
 */
class MolinaSessionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_EXIT) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            thread(name = "molina-exit") { AppExit.run("notification") }
            return START_NOT_STICKY
        }
        try {
            ensureChannel()
            val n = buildNotification()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            MolinaEvents.bug("app", "session-service-failed", e.message ?: e.javaClass.simpleName, e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Sesi Molina", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Notifikasi tetap selama Molina berjalan, dengan tombol Keluar"
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            flags,
        )
        val exit = PendingIntent.getService(
            this, 1,
            Intent(this, MolinaSessionService::class.java).setAction(ACTION_EXIT),
            flags,
        )
        val icon = com.molina.core.ui.R.drawable.ic_stat_molina_terminal
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle("Molina")
            .setContentText("Berjalan. Ketuk Keluar untuk menghentikan semua proses.")
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, icon), "Keluar", exit).build())
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "molina_session"
        private const val NOTIF_ID = 1001
        const val ACTION_EXIT = "com.molina.action.EXIT"

        /** Dipanggil dari Activity yang sedang tampil (aman untuk batas layanan latar Android 8+). */
        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, MolinaSessionService::class.java))
            } catch (e: Exception) {
                MolinaEvents.bug("app", "session-service-failed", e.message ?: e.javaClass.simpleName, e)
            }
        }
    }
}
