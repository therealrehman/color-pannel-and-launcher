package com.oklabs.oneui9launcher

import android.content.Context

/**
 * 6-hour rewarded lock for "Quick panel over other apps" (the swipe-up panel strip).
 * One rewarded ad turns the switch on for 6 hours. When the 6 hours end, the switch goes off again
 * (see [enforce]). Separate from the gradient unlock ([ThemeUnlock]).
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

    /** When the unlock has run out, the switch goes off, so it has to be unlocked again. */
    fun enforce(c: Context) {
        if (OverlayPanel.wanted(c) && !isOpen(c)) OverlayPanel.setWanted(c, false)
    }
}
