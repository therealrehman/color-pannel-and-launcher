package com.oklabs.oneui9launcher

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Step 9b: notification badges. [BadgeService] counts the notifications that are showing for each app and hands the numbers
 * to [Badges]; every [AppIconView] that has a [AppIconView.badgePackage] asks here and redraws when the numbers change.
 * Badges only keep the counts. Step 16c: while "Notifications in Quick panel" is on, the title and text are also held in memory for the panel (see [Notifs]), never saved or sent.
 * Needs the phone's "Notification access".
 */
object Badges {

    private val counts = HashMap<String, Int>()
    private val listeners = ArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    /** Notifications showing for [pkg], or 0 when badges are off. */
    fun count(pkg: String): Int =
        if (HomeStore.badgeMode == HomeStore.BADGE_OFF) 0 else (counts[pkg] ?: 0)

    fun addListener(listener: () -> Unit) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    /** New counts from the service (any thread). */
    fun publish(newCounts: Map<String, Int>) {
        main.post {
            counts.clear()
            counts.putAll(newCounts)
            for (listener in ArrayList(listeners)) listener()
        }
    }

    /** True when the person allowed this launcher to see notifications (Settings > Notification access). */
    fun accessGranted(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(':').any { ComponentName.unflattenFromString(it)?.packageName == context.packageName }
    }

    /** Opens the phone's "Notification access" list. */
    fun openAccessSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // a phone without that screen: nothing more to do
        }
    }
}

/** Listens to the phone's notifications (once the person allowed it) and reports how many each app has. */
class BadgeService : NotificationListenerService() {

    // Galaxy S26 Launcher rebind loop (copied): when the phone drops this listener, wait 5 s, then ask Android to connect it again
    // while the notification access is still on. At most 3 tries, at least 20 s apart; a connect cancels the tries.
    private val handler = Handler(Looper.getMainLooper())
    private var tries = 0
    private var lastTryAt = 0L
    private val rebind = Runnable { askAgain() }

    override fun onListenerConnected() {
        tries = 0
        handler.removeCallbacks(rebind)
        Notifs.service = this
        recount()
    }

    override fun onListenerDisconnected() {
        if (Notifs.service === this) Notifs.service = null
        Notifs.publish(emptyList())
        handler.removeCallbacks(rebind)
        handler.postDelayed(rebind, 5000L)
    }

    override fun onDestroy() {
        handler.removeCallbacks(rebind)
        if (Notifs.service === this) Notifs.service = null
        super.onDestroy()
    }

    /** True while the person still has notification access for this app. */
    private fun accessOn(): Boolean {
        val listed = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_NOTIFICATION_LISTENERS) ?: return false
        return listed.split(":").any { it.substringBefore("/") == packageName }
    }

    private fun askAgain() {
        if (tries >= 3 || !accessOn()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastTryAt < 20000L) {
            handler.postDelayed(rebind, 20000L - (now - lastTryAt))
            return
        }
        lastTryAt = now
        tries++
        val me = ComponentName(this, BadgeService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                if (tries == 1) requestUnbind(me)
                requestRebind(me)
            } else {
                // older phones: switch the listener off and on again, without stopping the app
                packageManager.setComponentEnabledSetting(me, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                packageManager.setComponentEnabledSetting(me, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
            }
        } catch (e: Exception) {
            // the phone refused: the next try, or the next restart, will do it
        }
        handler.postDelayed(rebind, 20000L) // check again; onListenerConnected cancels this
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        recount()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        recount()
    }

    /** Ongoing ones (music player, downloads) and group summaries do not count. */
    private fun recount() {
        val map = HashMap<String, Int>()
        val list = ArrayList<NotifItem>()
        // Step 16c: the text is only taken while "Notifications in Quick panel" is on.
        val prefs = getSharedPreferences("launcher", MODE_PRIVATE)
        val forPanel = prefs.getBoolean(Notifs.PREF_KEY, true) || prefs.getBoolean(NotificationPage.PREF_KEY, true)
        try {
            val showing = activeNotifications
            if (showing != null) {
                for (sbn in showing) {
                    val n = sbn.notification ?: continue
                    if (sbn.isOngoing) continue
                    if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) continue
                    map[sbn.packageName] = (map[sbn.packageName] ?: 0) + 1
                    if (forPanel && n.visibility != Notification.VISIBILITY_SECRET) {
                        val extras = n.extras
                        val title = NotifLogic.firstText(extras?.getCharSequence(Notification.EXTRA_TITLE))
                        val text = NotifLogic.firstText(
                            extras?.getCharSequence(Notification.EXTRA_TEXT),
                            extras?.getCharSequence(Notification.EXTRA_BIG_TEXT),
                            extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)
                        )
                        if (title.isNotEmpty() || text.isNotEmpty()) {
                            list.add(
                                NotifItem(
                                    sbn.key, sbn.packageName, title, text,
                                    if (sbn.postTime > 0L) sbn.postTime else System.currentTimeMillis(),
                                    sbn.isClearable, n.contentIntent,
                                    (n.flags and Notification.FLAG_AUTO_CANCEL) != 0
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // the system may refuse for a moment; the next change counts again
        }
        Badges.publish(map)
        Notifs.publish(NotifLogic.newestFirst(list) { it.whenMs })
    }
}
