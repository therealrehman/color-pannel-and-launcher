package com.oklabs.oneui9launcher

import android.app.Activity
import android.app.ActivityManager
import android.app.ApplicationExitInfo
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
import android.widget.Toast

/**
 * Keeps track of whether this launcher is the phone's Home app, and keeps a short log of what changed (Settings > Home app log).
 *
 * Step 90: "is it the Home app?" is answered by what the HOME button really opens (Android's answer to the HOME intent), not only by
 * the Home ROLE, because the role can still say "yes" while the HOME button opens the phone's own launcher.
 *
 * Step 91: the log also records when another app that can take the HOME button is installed, updated, switched on or off or
 * removed (HomeEventReceiver), and why Android or the phone maker last stopped this app's process. A full text report can be copied.
 *
 * Step 92b (Home Guard, calm version): when the HOME button stops opening this launcher, the guard opens Android's "Set as Home app?"
 * question exactly ONCE per episode, then only keeps a quiet notification (tap it to ask again). It never pops the dialog repeatedly.
 * The episode ends when the Home button opens this launcher again.
 */
object HomeKeeper {
    const val PREF_ON = "hk_on"
    private const val PREF_LOG = "hk_log"
    private const val PREF_WAS = "hk_was_default"
    private const val PREF_CANDIDATES = "hk_candidates"
    private const val PREF_GUARD_ASKED = "hk_guard_asked" // Step 92b: the dialog was already shown for the current "lost" episode
    private const val NOTE_ID = 4712
    private const val CHAN_HOME = "home_guard"
    private const val LOG_LINES = 40

    private fun prefs(c: Context) = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun wanted(c: Context): Boolean = prefs(c).getBoolean(PREF_ON, true)

    /** The Home ROLE as Android reports it (Android 10+): true, false, or null when this phone has no such role to read. */
    fun roleHeld(c: Context): Boolean? {
        if (Build.VERSION.SDK_INT < 29) return null
        return try {
            val roles = c.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) roles.isRoleHeld(RoleManager.ROLE_HOME) else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The package the HOME button opens right now: this launcher's package, another app's, "" when there is none, or "?" when it can not be read.
     * The manifest's <queries> entry for the HOME intent lets Android 11+ show this app the other launchers.
     */
    fun holder(c: Context): String = try {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        c.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName ?: ""
    } catch (e: Exception) {
        "?"
    }

    /** The same answer as [holder], in words for the log and for Settings. */
    fun holderLabel(c: Context): String {
        val who = holder(c)
        return when {
            who.isEmpty() -> "no app"
            who == "?" -> "an unknown app"
            else -> who
        }
    }

    /** True when the HOME button opens THIS launcher. The Home role decides only when the HOME button can not be read. */
    fun isDefault(c: Context): Boolean {
        val who = holder(c)
        if (who == "?") return roleHeld(c) ?: true // when in doubt do not nag
        return who == c.packageName
    }

    private fun roleText(c: Context): String {
        val r = roleHeld(c)
        return if (r == null) "no Home role on this phone" else if (r) "role: ours" else "role: not ours"
    }

    /** Step 91: the apps (package names) that can take the HOME button right now. */
    fun homeCandidatePackages(c: Context): Set<String> = try {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        c.packageManager.queryIntentActivities(home, 0).mapNotNull { it.activityInfo?.packageName }.toSet()
    } catch (e: Exception) {
        emptySet()
    }

    /** Step 91: true when this package has an activity that can take the HOME button right now. */
    fun isHomeCandidate(c: Context, pkg: String): Boolean = try {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg)
        c.packageManager.queryIntentActivities(home, 0).isNotEmpty()
    } catch (e: Exception) {
        false
    }

    /** Step 91: every package seen as a Home candidate so far, so that a removed one can still be named in the log. */
    fun candidates(c: Context): Set<String> =
        (prefs(c).getString(PREF_CANDIDATES, "") ?: "").split(",").filter { it.isNotBlank() }.toSet()

    /** Step 91: remembers the current candidates. Written only when something new is seen. */
    fun rememberCandidates(c: Context) {
        val before = candidates(c)
        val all = before + homeCandidatePackages(c)
        if (all != before) prefs(c).edit().putString(PREF_CANDIDATES, all.joinToString(",")).apply()
    }

    private fun stamp(ms: Long): String =
        java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.US).format(java.util.Date(ms))

    /** Step 91: why Android or the phone maker last stopped this app's process (Android 11 and newer). */
    fun lastExits(c: Context): String {
        if (Build.VERSION.SDK_INT < 30) return "not available before Android 11"
        return try {
            val am = c.getSystemService(ActivityManager::class.java)
            val list: List<ApplicationExitInfo> = am?.getHistoricalProcessExitReasons(c.packageName, 0, 3) ?: emptyList()
            val names = list.map { info ->
                val why = when (info.reason) {
                    ApplicationExitInfo.REASON_USER_REQUESTED -> "stopped by the user (force stop or clear)"
                    ApplicationExitInfo.REASON_OTHER -> "stopped by the system or the phone maker"
                    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "battery or CPU limit"
                    ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory"
                    ApplicationExitInfo.REASON_CRASH -> "crash"
                    ApplicationExitInfo.REASON_ANR -> "not responding"
                    ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "a service it depends on died"
                    else -> "reason " + info.reason
                }
                why + " at " + stamp(info.timestamp)
            }
            if (names.isEmpty()) "none recorded" else names.joinToString(" | ")
        } catch (e: Exception) {
            "could not read"
        }
    }

    private fun versionName(c: Context): String = try {
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    /** Step 91: everything needed to read the Home problem, as plain text. Settings > Home app log > tap = copy. */
    fun report(c: Context): String {
        val others = homeCandidatePackages(c).filter { it != c.packageName }.sorted()
        return buildString {
            append("Glassy Launcher ").append(versionName(c)).append('\n')
            append("Phone: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            append(", Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Home role: ").append(roleText(c)).append('\n')
            append("The HOME button opens: ").append(holderLabel(c)).append('\n')
            append("Other apps that can take HOME: ").append(if (others.isEmpty()) "none" else others.joinToString(", ")).append('\n')
            append("This app's last stops: ").append(lastExits(c)).append('\n')
            append("\nLog (newest last):\n").append(log(c).ifEmpty { "(empty)" }).append('\n')
        }
    }

    /** The last events, newest last (Settings > Home app log). */
    fun log(c: Context): String = prefs(c).getString(PREF_LOG, "") ?: ""

    fun addLog(c: Context, text: String) {
        val lines = (log(c).split("\n").filter { it.isNotBlank() } + (stamp(System.currentTimeMillis()) + "  " + text)).takeLast(LOG_LINES)
        prefs(c).edit().putString(PREF_LOG, lines.joinToString("\n")).apply()
    }

    /**
     * Step 90: a quiet LOG only. When the HOME button changes hands it writes one line (time, the app it opens now, whether the Home role
     * is still ours, and why this app last stopped). It never asks, shows anything or opens anything.
     */
    fun watch(c: Context) {
        try {
            if (!wanted(c)) return
            rememberCandidates(c)
            val now = isDefault(c)
            val was = prefs(c).getBoolean(PREF_WAS, true)
            if (now != was) {
                prefs(c).edit().putBoolean(PREF_WAS, now).apply()
                if (now) {
                    addLog(c, "the Home button opens this launcher again")
                } else {
                    val crash = if (CrashLog.read(c) != null) ", a crash note exists (see Settings)" else ""
                    addLog(c, "the Home button now opens " + holderLabel(c) + " (" + roleText(c) + "); this app's last stop: " + lastExits(c) + crash)
                }
            }
        } catch (e: Exception) {
            // a log line must never end the service
        }
    }

    /**
     * Called after a restart, an update and every 15-minute check. It never opens anything by itself: no Android dialog, no Home
     * settings page, no popup. When the HOME button does not open this launcher, it writes one log line per episode and shows a quiet
     * notification; only the person's tap on that notification starts [HomeRoleActivity].
     */
    fun guard(c: Context) {
        try {
            if (!wanted(c)) return
            if (isDefault(c)) {
                if (prefs(c).getBoolean(PREF_GUARD_ASKED, false)) {
                    prefs(c).edit().putBoolean(PREF_GUARD_ASKED, false).apply()
                    addLog(c, "the Home button opens this launcher again")
                }
                clearNote(c)
                return
            }
            if (!prefs(c).getBoolean(PREF_GUARD_ASKED, false)) {
                prefs(c).edit().putBoolean(PREF_GUARD_ASKED, true).apply()
                addLog(c, "the Home button now opens " + holderLabel(c) + "; quiet notification only, nothing is opened by itself")
            }
            notifyLost(c)
        } catch (e: Exception) {
            // the guard must never end the receiver
        }
    }

    /** Step 92b: a quiet notification "tap to make this launcher the Home app again"; it never pops up by itself. */
    private fun notifyLost(c: Context) {
        try {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val pi = PendingIntent.getActivity(
                c, 0, Intent(c, HomeRoleActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val b: Notification.Builder = if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(NotificationChannel(CHAN_HOME, "Home app", NotificationManager.IMPORTANCE_LOW))
                Notification.Builder(c, CHAN_HOME)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(c).setPriority(Notification.PRIORITY_LOW)
            }
            nm.notify(
                NOTE_ID,
                b.setSmallIcon(R.drawable.ic_stat_panel)
                    .setContentTitle("Home app changed")
                    .setContentText("Tap to make " + c.getString(R.string.app_name) + " the Home app again")
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()
            )
        } catch (e: Exception) {
            // no notification on this phone: nothing more the guard can do by itself
        }
    }

    /** Clears the guard notification. */
    fun clearNote(c: Context) {
        try {
            (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTE_ID)
        } catch (e: Exception) {
            // nothing to cancel
        }
    }

    /** Kept for the old callers: it only clears an old notification. */
    fun check(c: Context, mayOpenDialog: Boolean) {
        clearNote(c)
    }
}

/**
 * A see-through screen that makes this launcher the Home app. When the Home role is free it opens Android's own question. When the role
 * is already ours but the HOME button opens another app, only the Home app page can change that: a hint says to pick the other app first,
 * then this one. Started by the person (Set as default, Setup page, Quick panel switch), by the Step 92b guard after the phone drops the
 * Home app, or by tapping the guard notification.
 */
class HomeRoleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (HomeKeeper.isDefault(this)) {
            HomeKeeper.clearNote(this)
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
            Toast.makeText(
                this,
                "Pick another Home app first, then pick " + getString(R.string.app_name) + " again",
                Toast.LENGTH_LONG
            ).show()
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
        } else if (HomeKeeper.isDefault(this)) {
            HomeKeeper.clearNote(this)
        }
        finish()
    }
}
