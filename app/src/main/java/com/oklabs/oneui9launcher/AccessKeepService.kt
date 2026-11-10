package com.oklabs.oneui9launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Keeps the process of the Accessibility service (the panel's pull-down catch) in the foreground while the service is on, so that
 * the phone's battery cleaner is less likely to stop it. It runs only while the Accessibility service is connected, and it is stopped
 * when that service is disconnected. The notification is the lowest importance and can be hidden in the phone's settings.
 */
class AccessKeepService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            startAsForeground()
        } catch (e: Exception) {
            // the phone does not allow a foreground start now: the service is simply not kept alive
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "Quick panel pull-down", NotificationManager.IMPORTANCE_MIN)
            channel.setShowBadge(false)
            nm.createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            this, 3, Intent(this, HomeSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        builder
            .setSmallIcon(R.drawable.ic_stat_panel)
            .setContentTitle(getString(R.string.app_name) + " pull-down is on")
            .setContentText("Accessibility is on. Tap to open the settings")
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(open)
        val note = builder.build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTE_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTE_ID, note)
    }

    companion object {
        private const val CHANNEL = "quick_panel_access"
        private const val NOTE_ID = 4712

        /** Called by the Accessibility service: starts the keep-alive (never throws). */
        fun start(c: Context) {
            try {
                c.startForegroundService(Intent(c, AccessKeepService::class.java))
            } catch (e: Exception) {
                // the phone does not allow it now
            }
        }

        /** Called by the Accessibility service when it is disconnected. */
        fun stop(c: Context) {
            try {
                c.stopService(Intent(c, AccessKeepService::class.java))
            } catch (e: Exception) {
                // already stopped
            }
        }
    }
}
