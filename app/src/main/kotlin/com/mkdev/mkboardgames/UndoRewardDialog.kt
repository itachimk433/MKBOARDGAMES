package com.mkdev.mkboardgames

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

object UndoRewardDialog {
    private const val CONNECTION_ERROR =
        "Please Check Your Internet Connection and try again."

    fun show(
        activity: android.app.Activity,
        currentCredits: Int,
        onReward: () -> Int,
    ) {
        val dialog = Dialog(activity)
        val density = activity.resources.displayMetrics.density
        val padding = (24 * density).toInt()
        var credits = currentCredits.coerceIn(0, SettingsManager.MAX_UNDO_CREDITS)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#102C32"))
                cornerRadii = floatArrayOf(
                    18f * density, 18f * density,
                    18f * density, 18f * density,
                    0f, 0f, 0f, 0f,
                )
            }
        }
        val title = TextView(activity).apply {
            text = "Out of undos"
            setTextColor(Color.WHITE)
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val message = TextView(activity).apply {
            text = "Watch a short ad to get up to 2 more undo turns."
            setTextColor(Color.parseColor("#BFD0C6"))
            textSize = 16f
            setPadding(0, (12 * density).toInt(), 0, (24 * density).toInt())
        }
        val loading = ProgressBar(activity).apply {
            isIndeterminate = true
            visibility = android.view.View.GONE
        }
        val actions = LinearLayout(activity).apply {
            gravity = Gravity.END
        }
        val cancel = Button(activity).apply {
            text = "Not now"
            setOnClickListener { dialog.dismiss() }
        }
        lateinit var watch: Button
        watch = Button(activity).apply {
            text = "Watch ad"
            setOnClickListener {
                if (credits >= SettingsManager.MAX_UNDO_CREDITS) return@setOnClickListener
                isEnabled = false
                cancel.isEnabled = false
                text = "Checking…"
                loading.visibility = android.view.View.VISIBLE
                message.text = "Checking your connection and loading the ad…"
                AdManager.restoreFullscreen(activity)
                AdManager.showRewarded(
                    activity = activity,
                    onReward = {},
                    onUnavailable = {
                        if (!dialog.isShowing) return@showRewarded
                        loading.visibility = android.view.View.GONE
                        message.text = CONNECTION_ERROR
                        cancel.isEnabled = true
                        isEnabled = true
                        text = "Watch ad"
                    },
                    onAdFinished = { rewardEarned ->
                        if (!dialog.isShowing) return@showRewarded
                        message.text = "Verifying the ad…"
                        loading.visibility = android.view.View.VISIBLE
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!dialog.isShowing) return@postDelayed
                            loading.visibility = android.view.View.GONE
                            if (rewardEarned) {
                                credits = onReward().coerceIn(0, SettingsManager.MAX_UNDO_CREDITS)
                                title.text = "Add more undos?"
                                message.text = if (credits >= SettingsManager.MAX_UNDO_CREDITS) {
                                    "You have the maximum of ${SettingsManager.MAX_UNDO_CREDITS} undo turns."
                                } else {
                                    "You now have $credits undo turns. Watch another ad to add up to 2 more."
                                }
                                cancel.isEnabled = true
                                watch.isEnabled = credits < SettingsManager.MAX_UNDO_CREDITS
                                watch.text = if (watch.isEnabled) "Watch ad" else "Maximum reached"
                            } else {
                                message.text = "The ad could not be verified. Please watch it completely and try again."
                                cancel.isEnabled = true
                                watch.isEnabled = true
                                watch.text = "Watch ad"
                            }
                        }, 600L)
                    },
                )
            }
        }
        actions.addView(cancel)
        actions.addView(watch)
        root.addView(title)
        root.addView(message)
        root.addView(loading, LinearLayout.LayoutParams(
            (32 * density).toInt(),
            (32 * density).toInt(),
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = (16 * density).toInt()
        })
        root.addView(actions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        if (credits >= SettingsManager.MAX_UNDO_CREDITS) {
            message.text = "You have the maximum of ${SettingsManager.MAX_UNDO_CREDITS} undo turns."
            watch.isEnabled = false
            watch.text = "Maximum reached"
        }
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setOnDismissListener {
            AdManager.restoreFullscreen(activity)
        }
        dialog.setOnShowListener {
            dialog.window?.apply {
                setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (activity.resources.displayMetrics.heightPixels * 0.5f).toInt(),
                )
                setGravity(Gravity.BOTTOM)
                attributes = attributes.apply { dimAmount = 0.65f }
                addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }
        dialog.show()
    }
}