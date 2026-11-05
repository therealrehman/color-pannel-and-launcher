package com.oklabs.oneui9launcher

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import com.unity3d.mediation.LevelPlay
import com.unity3d.mediation.LevelPlayAdError
import com.unity3d.mediation.LevelPlayAdInfo
import com.unity3d.mediation.LevelPlayAdSize
import com.unity3d.mediation.LevelPlayBannerAdView
import com.unity3d.mediation.LevelPlayBannerAdViewListener
import com.unity3d.mediation.LevelPlayConfiguration
import com.unity3d.mediation.LevelPlayInitError
import com.unity3d.mediation.LevelPlayInitListener
import com.unity3d.mediation.LevelPlayInitRequest
import com.unity3d.mediation.LevelPlayInterstitialAd
import com.unity3d.mediation.LevelPlayInterstitialAdListener
import com.unity3d.mediation.LevelPlayReward
import com.unity3d.mediation.LevelPlayRewardedAd
import com.unity3d.mediation.LevelPlayRewardedAdListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Step 79c: Unity LevelPlay (Unity Ads) - Rewarded (unlocks the panel gradients, see [Unlock]),
 * Interstitial (once a day, when Settings opens) and Banner (bottom of the Settings screen).
 *
 * Corrected from step 79a/b: this app is "Glassy launcher" in the LevelPlay dashboard, not
 * "KeyAura" - the app key and all three placement names below were wrong before and are fixed here.
 *
 * Fill-rate rule (asked for in this step): an ad that fails to load, fails to show, or simply
 * never arrives must never permanently block the person. The rewarded flow below grants the
 * unlock anyway when that happens; the interstitial and banner just stay silent/invisible instead
 * of being shown.
 */
object Ads {

    private const val TAG = "Ads"

    // Glassy launcher, from the LevelPlay dashboard.
    private const val APP_KEY = "9c827b06-049a-4a11-9a8d-7a5d5631d50b"
    private const val PLACEMENT_REWARDED = "BP_Rewarded_Android"
    private const val PLACEMENT_INTERSTITIAL = "BP_Interstitial_Android"
    private const val PLACEMENT_BANNER = "BP_Banner_Android"

    /** How long [requestRewarded] waits for a late-arriving ad before granting the unlock anyway. */
    private const val NO_FILL_TIMEOUT_MS = 8_000L

    private const val KEY_INTERSTITIAL_DAY = "interstitial_shown_day"

    @Volatile private var initialized = false
    private var rewardedAd: LevelPlayRewardedAd? = null
    private var interstitialAd: LevelPlayInterstitialAd? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var waitingActivity: Activity? = null
    private var pendingGrant: (() -> Unit)? = null
    private var timeoutRunnable: Runnable? = null

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Call once, from [App.onCreate]. Starts the SDK and begins loading the rewarded + interstitial ads. */
    fun init(context: Context) {
        val initRequest = LevelPlayInitRequest.Builder(APP_KEY).build()
        LevelPlay.init(context, initRequest, object : LevelPlayInitListener {
            override fun onInitSuccess(configuration: LevelPlayConfiguration) {
                initialized = true
                Log.d(TAG, "LevelPlay init ok")
                createRewarded()
                createInterstitial()
            }

            override fun onInitFailed(error: LevelPlayInitError) {
                Log.w(TAG, "LevelPlay init failed: $error")
            }
        })
    }

    // ---------------------------------------------------------------- Rewarded (unlock gradients)

    private fun createRewarded() {
        val ad = LevelPlayRewardedAd(PLACEMENT_REWARDED)
        ad.setListener(object : LevelPlayRewardedAdListener {
            override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                // If someone is waiting (no ad was ready when they tapped), show it now instead of making them tap again.
                val activity = waitingActivity
                if (activity != null && pendingGrant != null) {
                    waitingActivity = null
                    cancelTimeout()
                    ad.showAd(activity, PLACEMENT_REWARDED)
                }
            }

            override fun onAdLoadFailed(error: LevelPlayAdError) {
                Log.w(TAG, "rewarded load failed (no fill?): $error")
                grantPendingIfAny() // no ad available - don't make the person wait forever for one
            }

            override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {}

            override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
                grantPendingIfAny() // couldn't show it either - still don't block the unlock
                ad.loadAd()
            }

            override fun onAdClicked(adInfo: LevelPlayAdInfo) {}

            override fun onAdClosed(adInfo: LevelPlayAdInfo) {
                ad.loadAd() // get the next one ready
            }

            override fun onAdRewarded(reward: LevelPlayReward, adInfo: LevelPlayAdInfo) {
                Log.d(TAG, "rewarded: ${reward.name} x${reward.amount}")
                grantPendingIfAny()
            }

            override fun onAdInfoChanged(adInfo: LevelPlayAdInfo) {}
        })
        rewardedAd = ad
        ad.loadAd()
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun grantPendingIfAny() {
        cancelTimeout()
        waitingActivity = null
        val grant = pendingGrant ?: return
        pendingGrant = null
        mainHandler.post { grant() }
    }

    /** True once a rewarded ad has finished loading and is ready to show right away. */
    fun isRewardedReady(): Boolean = rewardedAd?.isAdReady() == true

    /**
     * Shows the rewarded ad and calls [onGranted] once the person has watched it.
     *
     * If no ad is ready, it tries to load one; if that load fails (no fill) or simply takes longer
     * than [NO_FILL_TIMEOUT_MS], [onGranted] still runs - a missing ad must never lock the
     * gradients for good.
     */
    fun requestRewarded(activity: Activity, onGranted: () -> Unit) {
        val ad = rewardedAd
        if (ad != null && ad.isAdReady()) {
            pendingGrant = onGranted
            ad.showAd(activity, PLACEMENT_REWARDED)
            return
        }
        pendingGrant = onGranted
        waitingActivity = activity
        ad?.loadAd()
        cancelTimeout()
        val r = Runnable { grantPendingIfAny() }
        timeoutRunnable = r
        mainHandler.postDelayed(r, NO_FILL_TIMEOUT_MS)
    }

    // ---------------------------------------------------------------- Interstitial (once a day)

    private fun createInterstitial() {
        val ad = LevelPlayInterstitialAd(PLACEMENT_INTERSTITIAL)
        ad.setListener(object : LevelPlayInterstitialAdListener {
            override fun onAdLoaded(adInfo: LevelPlayAdInfo) {}
            override fun onAdLoadFailed(error: LevelPlayAdError) {}
            override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {}
            override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) { ad.loadAd() }
            override fun onAdClicked(adInfo: LevelPlayAdInfo) {}
            override fun onAdClosed(adInfo: LevelPlayAdInfo) { ad.loadAd() }
            override fun onAdInfoChanged(adInfo: LevelPlayAdInfo) {}
        })
        interstitialAd = ad
        ad.loadAd()
    }

    /**
     * Shows the interstitial the first time Settings opens on a given day - and does nothing at
     * all (no wait, no message) every other time, or if no ad happens to be ready. Call from
     * [HomeSettingsActivity].
     */
    fun maybeShowDailyInterstitial(activity: Activity) {
        val prefs = activity.getSharedPreferences("launcher", Context.MODE_PRIVATE)
        val day = today()
        if (prefs.getString(KEY_INTERSTITIAL_DAY, "") == day) return
        val ad = interstitialAd
        if (ad == null || !ad.isAdReady()) return // never worth waiting for - just skip today
        prefs.edit().putString(KEY_INTERSTITIAL_DAY, day).apply()
        ad.showAd(activity, PLACEMENT_INTERSTITIAL)
    }

    // ---------------------------------------------------------------- Banner (bottom of Settings)

    /**
     * Creates a banner view and loads it into [container]. If it fails to load, [container] is
     * simply left empty and hidden (no placeholder, no gap) - call from [HomeSettingsActivity].
     */
    fun attachBanner(activity: Activity, container: ViewGroup) {
        val config = LevelPlayBannerAdView.Config.Builder()
            .setAdSize(LevelPlayAdSize.BANNER)
            .setPlacementName(PLACEMENT_BANNER)
            .build()
        val banner = LevelPlayBannerAdView(activity, PLACEMENT_BANNER, config)
        banner.setBannerListener(object : LevelPlayBannerAdViewListener {
            override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                container.visibility = android.view.View.VISIBLE
            }
            override fun onAdLoadFailed(error: LevelPlayAdError) {
                container.visibility = android.view.View.GONE // no fill - take no space rather than show a gap
            }
            override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {}
            override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
                container.visibility = android.view.View.GONE
            }
            override fun onAdClicked(adInfo: LevelPlayAdInfo) {}
            override fun onAdLeftApplication(adInfo: LevelPlayAdInfo) {}
            override fun onAdExpanded(adInfo: LevelPlayAdInfo) {}
            override fun onAdCollapsed(adInfo: LevelPlayAdInfo) {}
        })
        container.visibility = android.view.View.GONE // hidden until (and unless) it actually loads
        container.removeAllViews()
        container.addView(banner)
        banner.loadAd()
    }
}
