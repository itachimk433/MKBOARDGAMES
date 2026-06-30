package com.mkdev.nexboard

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout

object AdManager {

    /**
     * Set to true once ads have been approved in Google Play.
     * Keep false for the initial Play Store submission.
     */
    internal const val ADS_ENABLED = false

    private const val ADMOB_BANNER_ID       = "ca-app-pub-4975030890366420/2351564268"
    private const val ADMOB_INTERSTITIAL_ID = "ca-app-pub-4975030890366420/6223360665"
    private const val HMS_BANNER_ID         = "g2jnehr5cv"
    private const val HMS_INTERSTITIAL_ID   = "v1nyf9xhiq"

    fun isHmsDevice(context: Context): Boolean = try {
        context.packageManager.getPackageInfo("com.huawei.hwid", 0)
        true
    } catch (_: Exception) { false }

    /**
     * Appends a banner ad to [container].
     * No-ops when ADS_ENABLED is false.
     */
    fun attachBanner(container: LinearLayout) {
        if (!ADS_ENABLED) return
        val ctx = container.context
        try {
            if (isHmsDevice(ctx)) {
                val banner = com.huawei.hms.ads.banner.BannerView(ctx).apply {
                    setAdId(HMS_BANNER_ID)
                    bannerAdSize = com.huawei.hms.ads.BannerAdSize.BANNER_SIZE_320_50
                }
                container.addView(banner, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                banner.loadAd(com.huawei.hms.ads.AdParam.Builder().build())
            } else {
                val banner = com.google.android.gms.ads.AdView(ctx).apply {
                    adUnitId = ADMOB_BANNER_ID
                    setAdSize(com.google.android.gms.ads.AdSize.BANNER)
                }
                container.addView(banner, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                banner.loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
            }
        } catch (_: Throwable) {}
    }

    /**
     * Pre-loads an interstitial ad. No-ops when ADS_ENABLED is false.
     */
    fun loadInterstitial(context: Context, onResult: (Any?) -> Unit) {
        if (!ADS_ENABLED) { onResult(null); return }
        try {
            if (isHmsDevice(context)) {
                val ad = com.huawei.hms.ads.InterstitialAd(context).apply {
                    adId = HMS_INTERSTITIAL_ID
                }
                ad.adListener = object : com.huawei.hms.ads.AdListener() {
                    override fun onAdLoaded()          { onResult(ad) }
                    override fun onAdFailed(code: Int) { onResult(null) }
                }
                ad.loadAd(com.huawei.hms.ads.AdParam.Builder().build())
            } else {
                com.google.android.gms.ads.interstitial.InterstitialAd.load(
                    context,
                    ADMOB_INTERSTITIAL_ID,
                    com.google.android.gms.ads.AdRequest.Builder().build(),
                    object : com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback() {
                        override fun onAdLoaded(ad: com.google.android.gms.ads.interstitial.InterstitialAd) {
                            onResult(ad)
                        }
                        override fun onAdFailedToLoad(err: com.google.android.gms.ads.LoadAdError) {
                            onResult(null)
                        }
                    }
                )
            }
        } catch (_: Throwable) { onResult(null) }
    }

    /** Shows a pre-loaded interstitial if ready. No-ops when ADS_ENABLED is false. */
    fun showInterstitial(context: Context, ad: Any?) {
        if (!ADS_ENABLED || ad == null) return
        try {
            when (ad) {
                is com.huawei.hms.ads.InterstitialAd ->
                    if (ad.isLoaded) ad.show()
                is com.google.android.gms.ads.interstitial.InterstitialAd ->
                    ad.show(context as androidx.appcompat.app.AppCompatActivity)
            }
        } catch (_: Throwable) {}
    }
}
