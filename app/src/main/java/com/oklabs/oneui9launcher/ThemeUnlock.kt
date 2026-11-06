package com.oklabs.oneui9launcher

import android.app.Activity
import android.content.Context

/**
 * Rewarded-ad lock for the gradient panel colours. One rewarded ad unlocks every gradient for 12 hours.
 * Solid colours and white glass are always free. If no ad can be shown, the gradients unlock anyway.
 * Step 84: the question is the [UnlockCard] (a real card), not a plain dialog.
 */
object ThemeUnlock {
    const val HOURS = 12L
    private const val KEY_UNTIL = "theme_unlock_until"

    private fun prefs(c: Context) = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    /** True while the 12-hour unlock is running. */
    fun isOpen(c: Context): Boolean = System.currentTimeMillis() < prefs(c).getLong(KEY_UNTIL, 0L)

    /** Whole hours left in the unlock (0 when it is not running). */
    fun hoursLeft(c: Context): Int {
        val left = prefs(c).getLong(KEY_UNTIL, 0L) - System.currentTimeMillis()
        return if (left <= 0L) 0 else (left / 3_600_000L).toInt()
    }

    /** True when colour [i] is a gradient and it is locked right now. */
    fun blocks(c: Context, i: Int): Boolean = PanelTheme.isGradient(i) && !isOpen(c)

    /** Opens the gradients for [HOURS] hours: after the rewarded ad, or when no ad could be shown. */
    fun grant(c: Context) {
        prefs(c).edit().putLong(KEY_UNTIL, System.currentTimeMillis() + HOURS * 60L * 60L * 1000L).apply()
    }

    /** Runs [then] at once when colour [i] is free. Otherwise shows the unlock card first. */
    fun gate(activity: Activity, i: Int, then: () -> Unit) {
        if (!blocks(activity, i)) {
            then()
            return
        }
        UnlockCard.show(activity, i, then)
    }
}
