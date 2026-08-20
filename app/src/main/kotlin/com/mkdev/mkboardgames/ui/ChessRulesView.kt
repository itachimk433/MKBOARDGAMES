package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

/**
 * A scrollable rules panel that matches the Chess setup and side-picker
 * surfaces instead of falling back to a platform AlertDialog.
 */
class ChessRulesView(
    context: Context,
    private val gameName: String,
    rulesText: String,
    private val gameLabel: String = "C H E S S",
    private val headerSymbol: String = "♛",
) : LinearLayout(context) {

    var onDone: (() -> Unit)? = null

    private val density = resources.displayMetrics.density

    init {
        orientation = VERTICAL
        setPadding((18f * density).toInt(), (18f * density).toInt(), (18f * density).toInt(), (14f * density).toInt())
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#102C32"), Color.parseColor("#0B1D25")),
        ).apply {
            cornerRadius = 12f * density
            setStroke((1f * density).toInt(), Color.parseColor("#2C5960"))
        }

        addView(TextView(context).apply {
            text = headerSymbol
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#E3B86A"))
            setTextSize(22f)
            setPadding(0, 0, 0, (1f * density).toInt())
        }, LayoutParams(LayoutParams.MATCH_PARENT, (32f * density).toInt()))

        addView(TextView(context).apply {
            text = gameLabel
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#E3B86A"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextSize(11f)
            letterSpacing = 0.18f
        }, LayoutParams(LayoutParams.MATCH_PARENT, (22f * density).toInt()))

        addView(TextView(context).apply {
            text = "How To Play $gameName"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextSize(25f)
        }, LayoutParams(LayoutParams.MATCH_PARENT, (46f * density).toInt()))

        addView(TextView(context).apply {
            text = "Learn the essentials before your first move."
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#9FB5B8"))
            setTextSize(12f)
        }, LayoutParams(LayoutParams.MATCH_PARENT, (30f * density).toInt()))

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            background = roundedBackground(Color.parseColor("#0D252B"), 8f)
        }
        scroll.addView(TextView(context).apply {
            text = rulesText
            setTextColor(Color.parseColor("#D7E1E0"))
            setTextSize(14f)
            setLineSpacing(4f * density, 1f)
            setPadding((16f * density).toInt(), (16f * density).toInt(), (16f * density).toInt(), (18f * density).toInt())
        })
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0).apply {
            weight = 1f
            topMargin = (8f * density).toInt()
        })

        addView(TextView(context).apply {
            text = "Got it"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#102C32"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextSize(14f)
            background = roundedBackground(Color.parseColor("#E3B86A"), 8f)
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onDone?.invoke()
            }
        }, LayoutParams(LayoutParams.MATCH_PARENT, (46f * density).toInt()).apply {
            topMargin = (12f * density).toInt()
        })
    }

    private fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * density
        }
}