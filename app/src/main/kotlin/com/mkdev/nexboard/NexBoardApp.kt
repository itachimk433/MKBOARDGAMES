package com.mkdev.nexboard

import android.app.Application
import android.content.pm.PackageManager

class NexBoardApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (hasHms(this)) {
            try {
                com.huawei.hms.ads.HwAds.init(this)
            } catch (_: Throwable) {}
        }
    }

    companion object {
        fun hasHms(context: android.content.Context): Boolean =
            try { context.packageManager.getPackageInfo("com.huawei.hwid", 0); true }
            catch (_: PackageManager.NameNotFoundException) { false }
    }
}
