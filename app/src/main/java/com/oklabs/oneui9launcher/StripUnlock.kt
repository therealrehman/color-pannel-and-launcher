package com.oklabs.oneui9launcher

import android.content.Context

/**
 * 6-hour rewarded lock for the swipe-up panel strip. The strip itself stays on screen (everywhere, Home included) while
 * "Quick panel over other apps" is on. This lock only decides what a swipe on the strip does: it opens the Quick panel while
 * the unlock runs, and otherwise shows the unlock card first. Separate from the gradient unlock ([ThemeUnlock]).
 */
object StripUnlock {
    const val HOURS = 6L
    private const val KEY_UNTIL = "strip_unlock_until"

    private fun prefs(c: Context) = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    /** True while the 6-hour unlock is running. */
    fun isOpen(c: Context): Boolean = System.currentTimeMillis() < prefs(c).getLong(KEY_UNTIL, 0L)

    /** Opens the strip for [HOURS] hours: after the rewarded ad, or when no ad could be shown. */
    fun grant(c: Context) {
        prefs(c).edit().putLong(KEY_UNTIL, System.currentTimeMillis() + HOURS * 60L * 60L * 1000L).apply()
    }
}
