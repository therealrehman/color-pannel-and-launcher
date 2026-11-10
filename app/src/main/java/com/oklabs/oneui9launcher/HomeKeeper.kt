package com.oklabs.oneui9launcher

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings

/**
 * Reads whether this launcher is the phone's Home app and keeps a short log of changes (Settings > Home app log).
 * It never asks again by itself: Android allows changing the Home app only by the person, in its own dialog, so the launcher asks only when
 * the person taps "Set as default" or switches on the Quick panel.
 */
object HomeKeeper {
    const val PREF_ON = "hk_on"
    private const val PREF_LAST_AUTO = "hk_last_auto"
    private const val CHANNEL = "home_keeper"
    private const val NOTE_ID = 4712
    private const val AUTO_GAP_MS = 20 * 60 * 1000L // documented: at most every 20 minutes
    private const val PREF_LOG = "hk_log"
    private const val PREF_WAS = "hk_was_default"

    private fun prefs(c: Context) = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun wanted(c: Context): Boolean = prefs(c).getBoolean(PREF_ON, true)

    /** True when this launcher is the phone's Home app right now. */
    fun isDefault(c: Context): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val roles = c.getSystemService(RoleManager::class.java)
                if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) return roles.isRoleHeld(RoleManager.ROLE_HOME)
            }
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val pkg = c.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            return pkg == c.packageName
        } catch (e: Exception) {
            return true // when in doubt do not nag
        }
    }

    /** The package that answers the Home button right now (who took over). */
    fun holder(c: Context): String = try {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        c.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    /** The last events, newest last: shown in Settings so we can see WHEN the Home role was lost and to WHICH launcher. */
    fun log(c: Context): String = prefs(c).getString(PREF_LOG, "") ?: ""

    fun addLog(c: Context, text: String) {
        val stamp = java.text.SimpleDateFormat("dd MMM HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        val lines = (log(c).split("\n").filter { it.isNotBlank() } + (stamp + "  " + text)).takeLast(12)
        prefs(c).edit().putString(PREF_LOG, lines.joinToString("\n")).apply()
    }

    /**
     * Called every few seconds by the panel service (cheap: one lookup). When the Home role changes hands it writes it to the log (with the
     * package that took it, and whether this app had crashed) and, at once, asks again (notification + Android's dialog).
     */
    fun watch(c: Context) {
        try {
            if (!wanted(c)) return
            val now = isDefault(c)
            val was = prefs(c).getBoolean(PREF_WAS, true)
            if (now != was) {
                prefs(c).edit().putBoolean(PREF_WAS, now).apply()
                if (now) addLog(c, "this launcher is the Home app again")
                else addLog(c, "LOST the Home role to " + holder(c) + (if (CrashLog.read(c) != null) " (a crash note exists: see Settings)" else ""))
            }
        } catch (e: Exception) {
            // never let a check end the service
        }
    }

    /**
     * No longer asks again by itself (no notification, no Android dialog on its own). The Home app changes only when the person taps
     * "Set as default" or the Quick panel switch. Kept as one call so the other places that call it still work.
     */
    fun check(c: Context, mayOpenDialog: Boolean) {
        try {
            (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTE_ID)
        } catch (e: Exception) {
            // nothing to cancel
        }
    }
}

/** A see-through screen that opens Android's "Home app" question and closes itself. */
class HomeRoleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (HomeKeeper.isDefault(this)) {
            finish()
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val roles = getSystemService(RoleManager::class.java)
                if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                    @Suppress("DEPRECATION")
                    startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 1)
                    return
                }
            }
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
                // no Settings app
            }
        }
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // "Don't ask again" (Android blocks the question after two refusals): open the phone's own Home app screen instead
        if (resultCode != RESULT_OK && !HomeKeeper.isDefault(this)) {
            try {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            } catch (e: Exception) {
                // no such screen
            }
        }
        finish()
    }
}
