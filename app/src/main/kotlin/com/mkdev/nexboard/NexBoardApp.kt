package com.mkdev.nexboard

import android.app.Application

class NexBoardApp : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            com.huawei.hms.ads.HwAds.init(this)
        } catch (_: Throwable) {}
    }
}
