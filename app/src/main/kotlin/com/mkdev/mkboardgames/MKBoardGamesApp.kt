package com.mkdev.mkboardgames

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks

class MKBoardGamesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        var startedActivities = 0
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (startedActivities++ == 0) MusicPlayer.onAppForeground()
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0) MusicPlayer.onAppBackground()
            }

            override fun onActivityCreated(activity: Activity, state: android.os.Bundle?) {
                if (activity.isMatchActivity()) {
                    MusicPlayer.enterMatch(activity)
                }
            }
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun Activity.isMatchActivity(): Boolean =
        this is GameActivity ||
            this is MorabarabaActivity ||
            this is TicTacToeActivity ||
            this is ConnectFourActivity ||
            this is LudoActivity ||
            this is MancalaActivity ||
            this is YoteActivity ||
            this is OnitamaActivity
}
