package com.mkdev.nexboard

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout

object AdManager {

    private const val ADMOB_BANNER_ID     = "ca-app-pub-4975030890366420/2351564268"
    private const val HMS_BANNER_ID       = "g2jnehr5cv"
    private const val HMS_INTERSTITIAL_ID = "v1nyf9xhiq"

    fun isHmsDevice(context: Context): Boolean = try {
        context.packageManager.getPackageInfo("com.huawei.hwid", 0)
        true
    } catch (_: Exception) { false }

    /**
     * Appends a banner ad to [container] using Huawei Ads on HMS devices and
     * Google AdMob on all other (GMS) devices. Errors are silently swallowed so
     * the host activity's layout is never broken by an ad failure.
     */
    fun attachBanner(container: LinearLayout) {
        val ctx = container.context
        try {
            if (isHmsDevice(ctx)) {
                val banner = com.huawei.hms.ads.banner.BannerView(ctx).apply {
                    setAdId(HMS_BANNER_ID)
                    bannerAdSize = com.huawei.hms.ads.BannerAdSize.BANNER_SIZE_320_50
                }
                container.addView(banner, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))
                banner.loadAd(com.huawei.hms.ads.AdParam.Builder().build())
            } else {
                val banner = com.google.android.gms.ads.AdView(ctx).apply {
                    adUnitId = ADMOB_BANNER_ID
                    setAdSize(com.google.android.gms.ads.AdSize.BANNER)
                }
                container.addView(banner, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))
                banner.loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
            }
        } catch (_: Throwable) {}
    }

    /**
     * Pre-loads an interstitial ad and delivers it via [onResult].
     * Only HMS devices load an interstitial (no Google interstitial unit was
     * registered yet). The caller receives null on GMS devices or on load failure.
     */
    fun loadInterstitial(context: Context, onResult: (Any?) -> Unit) {
        if (!isHmsDevice(context)) { onResult(null); return }
        try {
            val ad = com.huawei.hms.ads.InterstitialAd(context).apply {
                adId = HMS_INTERSTITIAL_ID
            }
            ad.adListener = object : com.huawei.hms.ads.AdListener() {
                override fun onAdLoaded()       { onResult(ad) }
                override fun onAdFailed(code: Int) { onResult(null) }
            }
            ad.loadAd(com.huawei.hms.ads.AdParam.Builder().build())
        } catch (_: Throwable) { onResult(null) }
    }

    /** Shows a pre-loaded interstitial if it is ready. Safe to call with null. */
    fun showInterstitial(ad: Any?) {
        if (ad == null) return
        try {
            (ad as? com.huawei.hms.ads.InterstitialAd)
                ?.takeIf { it.isLoaded }
                ?.show()
        } catch (_: Throwable) {}
    }
}
