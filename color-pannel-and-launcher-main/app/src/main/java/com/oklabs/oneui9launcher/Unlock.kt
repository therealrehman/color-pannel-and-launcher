package com.oklabs.oneui9launcher

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Step 79b (part 2 of 2): the daily "watch 2 rewarded ads" unlock for the panel gradients
 * (Settings > Panel colour, [PanelTheme.FREE_COUNT] onwards). Watching [ADS_NEEDED] ads unlocks
 * them until midnight; nothing is scheduled to re-lock them - [today] is just compared against the
 * saved date each time [isUnlocked] is read, so the day rolls over by itself.
 */
object Unlock {

    const val ADS_NEEDED = 2

    private const val KEY_COUNT_DAY = "unlock_count_day"
    private const val KEY_COUNT = "unlock_count"
    private const val KEY_UNLOCKED_DAY = "unlock_done_day"

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun prefs(context: Context) = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    /** True once [ADS_NEEDED] ads have been watched today. False again the first time this is read on a later day. */
    fun isUnlocked(context: Context): Boolean = prefs(context).getString(KEY_UNLOCKED_DAY, "") == today()

    /** How many ads watched today so far (resets to 0 on a new day, even before this is called). */
    fun adsWatchedToday(context: Context): Int {
        val p = prefs(context)
        return if (p.getString(KEY_COUNT_DAY, "") == today()) p.getInt(KEY_COUNT, 0) else 0
    }

    /** How many more ads today before it unlocks (0 once [isUnlocked] is true). */
    fun adsNeeded(context: Context): Int = (ADS_NEEDED - adsWatchedToday(context)).coerceAtLeast(0)

    /** Call once an ad has actually been watched to the end (from [Ads.showRewarded]'s callback). */
    fun onAdWatched(context: Context) {
        val day = today()
        val count = adsWatchedToday(context) + 1
        val edit = prefs(context).edit().putString(KEY_COUNT_DAY, day).putInt(KEY_COUNT, count)
        if (count >= ADS_NEEDED) edit.putString(KEY_UNLOCKED_DAY, day)
        edit.apply()
    }
}
