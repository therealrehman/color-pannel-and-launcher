package com.oklabs.oneui9launcher

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import com.huawei.hms.ads.AdParam
import com.huawei.hms.ads.BannerAdSize
import com.huawei.hms.ads.HwAds
import com.huawei.hms.ads.banner.BannerView
import com.huawei.hms.ads.reward.Reward
import com.huawei.hms.ads.reward.RewardAd
import com.huawei.hms.ads.reward.RewardAdLoadListener
import com.huawei.hms.ads.reward.RewardAdStatusListener

/**
 * Huawei Ads Kit (AppGallery build). The real ad slot IDs come from Petal Ads (Publisher Service) in AppGallery Connect.
 * Every call is guarded: if HMS Core or the ads cannot load, the caller gets [onUnavailable] and the app keeps working.
 *
 * Step 86: debug builds (the GitHub debug APK, which is debuggable) use Huawei's own TEST slots, which always return a test ad, so you can
 * see whether the integration works. The release build (AppGallery) uses the real slots. [lastError] says why an ad did not show.
 */
object HuaweiAds {
    /** The real ad slots (release build). */
    const val REWARDED_ID = "k2p31u2k9d"
    const val BANNER_ID = "w2x4afq04u"

    /** Huawei's documented test slots (debug build). */
    const val TEST_REWARDED_ID = "testx9dtjwj8hp"
    const val TEST_BANNER_ID = "testw6vs28auh3"

    private const val POLL_MS = 250L
    private const val MAX_WAIT_MS = 10_000L

    /** Why the last ad did not show, in words (empty when none has failed). Shown in the unlock card. */
    @Volatile
    var lastError: String = ""
        private set

    private var initDone = false

    /** True for the debug build (debuggable). */
    fun isDebugBuild(c: Context): Boolean = (c.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    fun rewardedId(c: Context): String = if (isDebugBuild(c)) TEST_REWARDED_ID else REWARDED_ID

    fun bannerId(c: Context): String = if (isDebugBuild(c)) TEST_BANNER_ID else BANNER_ID

    fun init(context: Context) {
        if (initDone) return
        try {
            HwAds.init(context.applicationContext)
            initDone = true
        } catch (e: Throwable) {
            // No HMS Core or no network: ads stay off, the app still works.
            lastError = "Huawei Ads could not start (" + e.javaClass.simpleName + ")"
        }
    }

    /**
     * Loads a rewarded ad and shows it. [onRewarded] runs once the person has earned the reward.
     * [onUnavailable] runs when no ad can be shown (no fill, no network, no HMS Core). Closing the ad without finishing it does nothing.
     */
    fun showRewarded(activity: Activity, onRewarded: () -> Unit, onUnavailable: () -> Unit) {
        lastError = ""
        init(activity)
        val ad = try {
            RewardAd(activity, rewardedId(activity))
        } catch (e: Throwable) {
            lastError = "Huawei Ads could not start (" + e.javaClass.simpleName + ")"
            onUnavailable()
            return
        }

        val handler = Handler(Looper.getMainLooper())
        var state = 0 // 0 = loading, 1 = showing, 2 = finished
        var waited = 0L

        fun unavailable() {
            if (state == 0) {
                state = 2
                onUnavailable()
            }
        }

        val listener = object : RewardAdStatusListener() {
            override fun onRewarded(reward: Reward) {
                onRewarded()
            }

            override fun onRewardAdClosed() {
                state = 2
            }
        }

        // Load failures come from RewardAdLoadListener (not from the status listener).
        val loadListener = object : RewardAdLoadListener() {
            override fun onRewardAdFailedToLoad(errorCode: Int) {
                lastError = "Huawei sent no ad, error " + errorCode
                unavailable()
            }
        }

        ad.loadAd(AdParam.Builder().build(), loadListener)

        // Wait until the ad is ready (up to 10 s), then show it on the main thread.
        val poll = object : Runnable {
            override fun run() {
                if (state != 0) return
                if (ad.isLoaded) {
                    state = 1
                    try {
                        ad.show(activity, listener)
                    } catch (e: Throwable) {
                        lastError = "the ad could not be shown"
                        state = 2
                        onUnavailable()
                    }
                    return
                }
                waited += POLL_MS
                if (waited >= MAX_WAIT_MS) {
                    lastError = "no ad was ready in 10 seconds"
                    unavailable()
                    return
                }
                handler.postDelayed(this, POLL_MS)
            }
        }
        handler.post(poll)
    }

    /** A banner ad view, ready to add to a layout. Not placed anywhere yet. */
    fun createBanner(context: Context): BannerView {
        init(context)
        return BannerView(context).apply {
            setAdId(bannerId(context))
            setBannerAdSize(BannerAdSize.BANNER_SIZE_320_50)
            loadAd(AdParam.Builder().build())
        }
    }
}
