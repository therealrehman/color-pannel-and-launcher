package com.oklabs.oneui9launcher

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.huawei.hms.ads.AdParam
import com.huawei.hms.ads.BannerAdSize
import com.huawei.hms.ads.HwAds
import com.huawei.hms.ads.banner.BannerView
import com.huawei.hms.ads.reward.Reward
import com.huawei.hms.ads.reward.RewardAd
import com.huawei.hms.ads.reward.RewardAdStatusListener

/**
 * Huawei Ads Kit (AppGallery build). The two ad unit IDs come from Petal Ads (Publisher Service).
 * Every call is guarded: if HMS Core or the ads cannot load, the caller gets [onUnavailable] and the app keeps working.
 */
object HuaweiAds {
    const val REWARDED_ID = "k2p31u2k9d"
    const val BANNER_ID = "w2x4afq04u"

    private const val POLL_MS = 250L
    private const val MAX_WAIT_MS = 10_000L

    private var initDone = false

    fun init(context: Context) {
        if (initDone) return
        try {
            HwAds.init(context.applicationContext)
            initDone = true
        } catch (e: Throwable) {
            // No HMS Core or no network: ads stay off, the app still works.
        }
    }

    /**
     * Loads a rewarded ad and shows it. [onRewarded] runs once the person has earned the reward.
     * [onUnavailable] runs when no ad can be shown (no fill, no network, no HMS Core). Closing the ad without finishing it does nothing.
     */
    fun showRewarded(activity: Activity, onRewarded: () -> Unit, onUnavailable: () -> Unit) {
        init(activity)
        val ad = try {
            RewardAd(activity, REWARDED_ID)
        } catch (e: Throwable) {
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

            override fun onRewardAdFailedToLoad(errorCode: Int) {
                unavailable()
            }
        }

        ad.loadAd(AdParam.Builder().build())

        // Wait until the ad is ready (up to 10 s), then show it on the main thread.
        val poll = object : Runnable {
            override fun run() {
                if (state != 0) return
                if (ad.isLoaded) {
                    state = 1
                    try {
                        ad.show(activity, listener)
                    } catch (e: Throwable) {
                        state = 2
                        onUnavailable()
                    }
                    return
                }
                waited += POLL_MS
                if (waited >= MAX_WAIT_MS) {
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
            setAdId(BANNER_ID)
            setBannerAdSize(BannerAdSize.BANNER_SIZE_320_50)
            loadAd(AdParam.Builder().build())
        }
    }
}
