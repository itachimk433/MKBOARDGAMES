package com.mkdev.mkboardgames

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdManager {
    // Official Google test IDs. Replace these before publishing the app.
    private const val TEST_APP_OPEN_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"
    private const val TEST_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_REWARDED_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    private const val BANNER_TAG = "mkboardgames_banner"
    private const val APP_OPEN_MAX_AGE_MS = 4L * 60L * 60L * 1000L

    private var rewardedAd: RewardedAd? = null
    private var rewardedLoadInProgress = false
    private val rewardedLoadCallbacks = mutableListOf<(Boolean) -> Unit>()
    private var applicationContext: Context? = null
    private var mobileAdsInitialized = false
    private var appOpenAd: AppOpenAd? = null
    private var appOpenLoadInProgress = false
    private var appOpenLoadedAt = 0L
    private var resumedActivity: Activity? = null
    private var shouldShowAppOpen = false
    private var isShowingAppOpen = false

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        MobileAds.initialize(applicationContext!!) {
            mobileAdsInitialized = true
            loadRewarded(applicationContext!!)
            loadAppOpen(applicationContext!!)
        }
    }

    /**
     * Adds the banner as a normal child of the match's vertical layout.
     * This reserves space below the board instead of drawing over it.
     */
    fun attachBanner(container: LinearLayout) {
        if (container.findViewWithTag<AdView>(BANNER_TAG) != null) return
        val adView = AdView(container.context).apply {
            tag = BANNER_TAG
            setAdSize(AdSize.BANNER)
            adUnitId = TEST_BANNER_UNIT_ID
        }
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

    fun onAppForeground(activity: Activity) {
        shouldShowAppOpen = true
        if (mobileAdsInitialized) {
            loadAppOpen(activity.applicationContext)
            maybeShowAppOpen()
        }
    }

    fun onActivityResumed(activity: Activity) {
        resumedActivity = activity
        maybeShowAppOpen()
    }

    fun onActivityPaused(activity: Activity) {
        if (resumedActivity === activity) resumedActivity = null
    }

    fun onAppBackground() {
        resumedActivity = null
        shouldShowAppOpen = false
    }

    // Existing game-over interstitial hooks stay disabled.
    fun loadInterstitial(context: Context, onResult: (Any?) -> Unit) = onResult(null)
    fun showInterstitial(context: Context, ad: Any?) = Unit

    private fun loadAppOpen(context: Context) {
        if (!mobileAdsInitialized || appOpenLoadInProgress || isFreshAppOpenReady()) return
        appOpenLoadInProgress = true
        AppOpenAd.load(
            context,
            TEST_APP_OPEN_UNIT_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    appOpenLoadedAt = System.currentTimeMillis()
                    appOpenLoadInProgress = false
                    maybeShowAppOpen()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpenLoadInProgress = false
                }
            },
        )
    }

    private fun isFreshAppOpenReady(): Boolean {
        return appOpenAd != null &&
            System.currentTimeMillis() - appOpenLoadedAt < APP_OPEN_MAX_AGE_MS
    }

    private fun maybeShowAppOpen() {
        val activity = resumedActivity ?: return
        if (!shouldShowAppOpen || isShowingAppOpen) return
        val ad = appOpenAd
        if (ad == null || !isFreshAppOpenReady()) {
            loadAppOpen(activity.applicationContext)
            return
        }

        appOpenAd = null
        shouldShowAppOpen = false
        isShowingAppOpen = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                isShowingAppOpen = false
                loadAppOpen(activity.applicationContext)
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                isShowingAppOpen = false
                loadAppOpen(activity.applicationContext)
            }
        }
        ad.show(activity)
    }

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
                restoreFullscreen(activity)
                loadRewarded(activity.applicationContext)
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                restoreFullscreen(activity)
                loadRewarded(activity.applicationContext)
                onUnavailable()
            }
        }
        cachedAd.show(activity) { onReward() }
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
