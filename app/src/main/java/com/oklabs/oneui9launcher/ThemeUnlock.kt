package com.oklabs.oneui9launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.widget.Toast

/**
 * Rewarded-ad lock for the gradient panel colours. One rewarded ad unlocks every gradient for 12 hours.
 * Solid colours and white glass are always free. If no ad can be shown, the gradients unlock anyway.
 */
object ThemeUnlock {
    const val HOURS = 12L
    private const val KEY_UNTIL = "theme_unlock_until"

    private fun prefs(c: Context) = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    /** True while the 12-hour unlock is running. */
    fun isOpen(c: Context): Boolean = System.currentTimeMillis() < prefs(c).getLong(KEY_UNTIL, 0L)

    /** True when colour [i] is a gradient and it is locked right now. */
    fun blocks(c: Context, i: Int): Boolean = PanelTheme.isGradient(i) && !isOpen(c)

    private fun unlock(c: Context) {
        prefs(c).edit().putLong(KEY_UNTIL, System.currentTimeMillis() + HOURS * 60L * 60L * 1000L).apply()
    }

    /** Runs [then] at once when colour [i] is free. Otherwise asks the person to watch one ad first. */
    fun gate(activity: Activity, i: Int, then: () -> Unit) {
        if (!blocks(activity, i)) {
            then()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("Unlock gradient colours")
            .setMessage("Watch a short ad to unlock all gradient panel colours for $HOURS hours.")
            .setPositiveButton("Watch ad") { _, _ ->
                HuaweiAds.showRewarded(
                    activity,
                    onRewarded = {
                        unlock(activity)
                        then()
                    },
                    onUnavailable = {
                        unlock(activity)
                        Toast.makeText(activity, "No ad is available right now. Gradients are unlocked.", Toast.LENGTH_LONG).show()
                        then()
                    }
                )
            }
            .setNegativeButton("Not now", null)
            .show()
    }
}
