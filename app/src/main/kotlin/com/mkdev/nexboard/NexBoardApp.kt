package com.mkdev.nexboard

import android.app.Application

class NexBoardApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Ad SDKs are only initialized when ads are actually enabled.
        // Keeping this guarded ensures no ad data is collected in ad-free builds.
        if (AdManager.ADS_ENABLED) {
            try { com.huawei.hms.ads.HwAds.init(this) } catch (_: Throwable) {}
            try { com.google.android.gms.ads.MobileAds.initialize(this) {} } catch (_: Throwable) {}
        }
    }
}
