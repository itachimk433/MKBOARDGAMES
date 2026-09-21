package com.mkdev.mkboardgames

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

object UndoRewardDialog {
    fun show(activity: android.app.Activity, onReward: () -> Unit) {
        val dialog = Dialog(activity)
        val density = activity.resources.displayMetrics.density
        val padding = (24 * density).toInt()
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
            text = "Watch a short test ad to get 1 more undo turn."
            setTextColor(Color.parseColor("#BFD0C6"))
            textSize = 16f
            setPadding(0, (12 * density).toInt(), 0, (24 * density).toInt())
        }
        val actions = LinearLayout(activity).apply {
            gravity = Gravity.END
        }
        val cancel = Button(activity).apply {
            text = "Not now"
            setOnClickListener { dialog.dismiss() }
        }
        val watch = Button(activity).apply {
            text = "Watch ad"
            setOnClickListener {
                isEnabled = false
                text = "Loading ad…"
                dialog.dismiss()
                AdManager.showRewarded(
                    activity = activity,
                    onReward = onReward,
                    onUnavailable = {
                        Toast.makeText(
                            activity,
                            "The ad is not ready yet. Try again in a moment.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
        }
        actions.addView(cancel)
        actions.addView(watch)
        root.addView(title)
        root.addView(message)
        root.addView(actions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
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