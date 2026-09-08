package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import com.mkdev.mkboardgames.games.onitama.OnitamaCards

/**
 * A scrollable reference gallery for the complete Onitama movement deck.
 * Each supplied card illustration gets its own full-width row.
 */
class OnitamaCardsView(
    context: Context,
) : FrameLayout(context) {

    var onBack: (() -> Unit)? = null

    private val density = resources.displayMetrics.density

    init {
        setBackgroundColor(Color.TRANSPARENT)
        addView(OnitamaAtmosphereView(context), LayoutParams(-1, -1))

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16f), dp(14f), dp(16f), dp(16f))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#17677F"), Color.parseColor("#0C3344")),
            ).apply {
                cornerRadius = dp(26f).toFloat()
                setStroke(dp(1f), Color.argb(70, 180, 213, 207))
            }
        }

        panel.addView(TextView(context).apply {
            text = "ONITAMA CARDS"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#FFE09C"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextSize(22f)
            letterSpacing = 0.08f
        }, LinearLayout.LayoutParams(-1, dp(38f)))

        panel.addView(TextView(context).apply {
            text = "Scroll through the complete movement deck"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#D8E9E7"))
            setTextSize(12f)
        }, LinearLayout.LayoutParams(-1, dp(28f)))

        val scroll = ScrollView(context).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = false
            setPadding(0, dp(8f), 0, dp(8f))
            background = roundedBackground(Color.argb(105, 7, 34, 47), 12f)
        }
        val cardList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
        }

        OnitamaCards.all.forEachIndexed { index, card ->
            val cardImage = ImageView(context).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "${card.name} movement card"
                setImageDrawable(loadCard(card.assetName))
                setPadding(0, 0, 0, 0)
                background = roundedBackground(Color.argb(85, 4, 23, 31), 8f)
            }
            cardList.addView(cardImage, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = if (index == 0) 0 else dp(8f)
            })
        }

        cardList.addView(TextView(context).apply {
            text = "Back"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#4A1714"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextSize(18f)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#F5D49A"),
                    Color.parseColor("#C8894C"),
                    Color.parseColor("#A76438"),
                ),
            ).apply {
                cornerRadius = dp(14f).toFloat()
                setStroke(dp(1f), Color.parseColor("#733A25"))
            }
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                SoundPlayer.play("ui_click")
                onBack?.invoke()
            }
        }, LinearLayout.LayoutParams(-1, dp(50f)).apply {
            topMargin = dp(16f)
            bottomMargin = dp(8f)
        })

        scroll.addView(cardList, ScrollView.LayoutParams(-1, -2))
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0).apply {
            weight = 1f
            topMargin = dp(8f)
        })

        addView(panel, LayoutParams(-1, -1).apply {
            setMargins(dp(22f), dp(24f), dp(22f), dp(24f))
        })
    }

    private fun loadCard(assetName: String?): Drawable? {
        if (assetName == null) return null
        return runCatching {
            context.assets.open(assetName).use { stream ->
                Drawable.createFromStream(stream, assetName)
            }
        }.getOrNull()
    }

    private fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * density
        }

    private fun dp(value: Float): Int = (value * density).toInt()
}