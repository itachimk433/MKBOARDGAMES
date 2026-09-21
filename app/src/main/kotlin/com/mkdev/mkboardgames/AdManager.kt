package com.mkdev.mkboardgames

import android.app.Activity
import android.content.Context
import android.widget.LinearLayout
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdManager {
    // Official Google test IDs. Replace these before publishing the app.
    private const val TEST_REWARDED_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    private var rewardedAd: RewardedAd? = null
    private var rewardedLoadInProgress = false
    private val rewardedLoadCallbacks = mutableListOf<(Boolean) -> Unit>()

    fun initialize(context: Context) {
        MobileAds.initialize(context.applicationContext) {
            loadRewarded(context.applicationContext)
        }
    }

    fun attachBanner(container: LinearLayout) {}

    // Existing game-over interstitial hooks stay disabled; only rewarded
    // undo ads are enabled by this feature.
    fun loadInterstitial(context: Context, onResult: (Any?) -> Unit) = onResult(null)
    fun showInterstitial(context: Context, ad: Any?) = Unit

    /**
     * Shows a rewarded test ad, loading one first when necessary. The reward
     * callback is only called by the SDK after the viewer earns the reward.
     */
    fun showRewarded(
        activity: Activity,
        onReward: () -> Unit,
        onUnavailable: () -> Unit,
    ) {
        val cachedAd = rewardedAd
        if (cachedAd == null) {
            loadRewarded(activity) { loaded ->
                if (loaded) {
                    showRewarded(activity, onReward, onUnavailable)
                } else {
                    onUnavailable()
                }
            }
            return
        }

        rewardedAd = null
        cachedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadRewarded(activity.applicationContext)
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                loadRewarded(activity.applicationContext)
                onUnavailable()
            }
        }
        cachedAd.show(activity) { onReward() }
    }

    fun loadRewarded(
        context: Context,
        callback: (Boolean) -> Unit = {},
    ) {
        rewardedAd?.let {
            callback(true)
            return
        }
        rewardedLoadCallbacks += callback
        if (rewardedLoadInProgress) return
        rewardedLoadInProgress = true
        RewardedAd.load(
            context,
            TEST_REWARDED_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    finishRewardedLoad(true)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    finishRewardedLoad(false)
                }
            },
        )
    }

    private fun finishRewardedLoad(loaded: Boolean) {
        rewardedLoadInProgress = false
        val callbacks = rewardedLoadCallbacks.toList()
        rewardedLoadCallbacks.clear()
        callbacks.forEach { it(loaded) }
    }
}
