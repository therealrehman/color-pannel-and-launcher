package com.oklabs.oneui9launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Step 91 (final): when an app that can take the HOME button is installed, updated, switched on or off, or removed, one line goes into
 * the Home app log, with the time. These are package broadcasts only: it shows nothing, asks nothing and opens nothing.
 */
class HomeEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            logPackageEvent(context, intent)
        } catch (e: Exception) {
            // a log line must never crash the app (for example while the phone is still locked after a restart)
        }
    }

    private fun logPackageEvent(context: Context, intent: Intent?) {
        val event = intent ?: return
        val pkg = event.data?.schemeSpecificPart ?: return
        if (pkg == context.packageName) return
        // an update sends "removed" with EXTRA_REPLACING first; the "replaced" event that follows is the one to log
        if (event.action == Intent.ACTION_PACKAGE_REMOVED && event.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
        val now = HomeKeeper.isHomeCandidate(context, pkg)
        val before = HomeKeeper.candidates(context).contains(pkg)
        if (!now && !before) return
        val what = when (event.action) {
            Intent.ACTION_PACKAGE_ADDED -> "was installed"
            Intent.ACTION_PACKAGE_REPLACED -> "was updated"
            Intent.ACTION_PACKAGE_REMOVED -> "was removed"
            Intent.ACTION_PACKAGE_CHANGED -> "was switched " + (if (now) "on" else "off")
            else -> "changed"
        }
        HomeKeeper.addLog(context, pkg + " " + what + " (it " + (if (now) "can take" else "can no longer take") + " the Home button)")
        HomeKeeper.rememberCandidates(context)
    }
}
