package com.mkdev.mkboardgames

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdManager {
    // Official Google test IDs. Replace these before publishing the app.
    private const val TEST_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_REWARDED_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    private const val BANNER_TAG = "mkboardgames_banner"

    private var rewardedAd: RewardedAd? = null
    private var rewardedLoadInProgress = false
    private val rewardedLoadCallbacks = mutableListOf<(Boolean) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var rewardedLoadRequest = 0L
    private var applicationContext: Context? = null
    private var mobileAdsInitialized = false
    private var resumedActivity: Activity? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        if (SettingsManager.isAdsRemoved(context)) return
        MobileAds.initialize(applicationContext!!) {
            mobileAdsInitialized = true
            loadRewarded(applicationContext!!)
        }
    }

    /**
     * Adds the banner as a normal child of the match's vertical layout.
     * This reserves space below the board instead of drawing over it.
     */
    fun attachBanner(container: LinearLayout) {
        if (SettingsManager.isAdsRemoved(container.context)) return
        if (container.findViewWithTag<AdView>(BANNER_TAG) != null) return
        val adView = createBanner(container.context)
        container.addView(
            adView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            },
        )
        adView.loadAd(AdRequest.Builder().build())
    }

    /**
     * Adds a banner over the game surface without participating in its
     * measurement. This keeps the board and pieces fixed when the ad loads.
     */
    fun attachBannerOverlay(container: FrameLayout) {
        if (SettingsManager.isAdsRemoved(container.context)) return
        if (container.findViewWithTag<AdView>(BANNER_TAG) != null) return
        val adView = createBanner(container.context)
        container.addView(
            adView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            },
        )
        adView.loadAd(AdRequest.Builder().build())
    }

    private fun createBanner(context: Context): AdView =
        AdView(context).apply {
            tag = BANNER_TAG
            setAdSize(AdSize.BANNER)
            adUnitId = TEST_BANNER_UNIT_ID
        }

    fun onAppForeground(activity: Activity) {
        // App-open ads are intentionally disabled. Opening or returning to the
        // app should never interrupt the user with a full-screen advertisement.
    }

    fun onActivityResumed(activity: Activity) {
        resumedActivity = activity
    }

    fun onActivityPaused(activity: Activity) {
        if (resumedActivity === activity) resumedActivity = null
    }

    fun onAppBackground() {
        resumedActivity = null
    }

    // Existing game-over interstitial hooks stay disabled.
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
        onAdFinished: (rewardEarned: Boolean) -> Unit = {},
    ) {
        if (SettingsManager.isAdsRemoved(activity)) {
            onReward()
            onAdFinished(true)
            return
        }
        val cachedAd = rewardedAd
        if (cachedAd == null) {
            loadRewarded(activity) { loaded ->
                if (loaded) {
                    showRewarded(activity, onReward, onUnavailable, onAdFinished)
                } else {
                    onUnavailable()
                }
            }
            return
        }

        rewardedAd = null
        var rewardEarned = false
        cachedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                restoreFullscreen(activity)
                loadRewarded(activity.applicationContext)
                onAdFinished(rewardEarned)
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                restoreFullscreen(activity)
                loadRewarded(activity.applicationContext)
                onUnavailable()
            }
        }
        cachedAd.show(activity) {
            rewardEarned = true
            onReward()
        }
    }

    /**
     * Rewarded ads and their parent Dialog can temporarily clear immersive
     * flags. Restore the match window after either one closes.
     */
    fun restoreFullscreen(activity: Activity) {
        val flags = (
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        activity.window.decorView.post {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = flags
        }
        activity.window.decorView.postDelayed({
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = flags
        }, 250L)
    }

    fun loadRewarded(
        context: Context,
        callback: (Boolean) -> Unit = {},
    ) {
        if (SettingsManager.isAdsRemoved(context)) {
            callback(true)
            return
        }
        rewardedAd?.let {
            callback(true)
            return
        }
        rewardedLoadCallbacks += callback
        if (rewardedLoadInProgress) return
        rewardedLoadInProgress = true
        val request = ++rewardedLoadRequest
        mainHandler.postDelayed({
            if (rewardedLoadInProgress && rewardedLoadRequest == request) {
                finishRewardedLoad(false)
            }
        }, REWARDED_LOAD_TIMEOUT_MS)
        RewardedAd.load(
            context,
            TEST_REWARDED_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    if (rewardedLoadInProgress && rewardedLoadRequest == request) {
                        finishRewardedLoad(true)
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (rewardedLoadInProgress && rewardedLoadRequest == request) {
                        finishRewardedLoad(false)
                    }
                }
            },
        )
    }

    private fun finishRewardedLoad(loaded: Boolean) {
        rewardedLoadInProgress = false
        rewardedLoadRequest++
        val callbacks = rewardedLoadCallbacks.toList()
        rewardedLoadCallbacks.clear()
        callbacks.forEach { it(loaded) }
    }

    private const val REWARDED_LOAD_TIMEOUT_MS = 10_000L
}
