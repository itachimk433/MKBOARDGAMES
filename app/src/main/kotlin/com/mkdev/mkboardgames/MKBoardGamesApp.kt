package com.mkdev.mkboardgames

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks

class MKBoardGamesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AdManager.initialize(this)
        var startedActivities = 0
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (startedActivities++ == 0) {
                    MusicPlayer.onAppForeground()
                    AdManager.onAppForeground(activity)
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0) {
                    MusicPlayer.onAppBackground()
                    AdManager.onAppBackground()
                }
            }

            override fun onActivityCreated(activity: Activity, state: android.os.Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = AdManager.onActivityResumed(activity)
            override fun onActivityPaused(activity: Activity) = AdManager.onActivityPaused(activity)
            override fun onActivitySaveInstanceState(activity: Activity, state: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
