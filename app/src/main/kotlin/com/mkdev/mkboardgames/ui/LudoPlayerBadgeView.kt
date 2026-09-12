package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.roundToInt

/**
 * The avatar portion of a Ludo player control.
 *
 * The surrounding frame and player name are drawn by LudoPlayerControlView so
 * the border can wrap both the avatar and the die without text collisions.
 */
class LudoPlayerBadgeView(context: Context) : View(context) {
    var label: String = ""
        set(value) {
            field = value
            contentDescription = "$value player profile"
            invalidate()
        }

    var accentColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    var facesOppositeSide: Boolean = false
        set(value) {
            field = value
            rotation = if (value) 180f else 0f
            invalidate()
        }

    var isActive: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        minimumHeight = dp(52)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (height * 0.31f).coerceAtLeast(dp(12).toFloat())

        avatarPaint.color = accentColor
        avatarPaint.style = Paint.Style.FILL
        avatarPaint.clearShadowLayer()
        if (isActive) {
            avatarPaint.setShadowLayer(
                dp(7).toFloat(),
                0f,
                0f,
                Color.argb(
                    225,
                    Color.red(accentColor),
                    Color.green(accentColor),
                    Color.blue(accentColor),
                ),
            )
        }
        canvas.drawCircle(centerX, centerY, radius, avatarPaint)

        ringPaint.color = Color.argb(245, 255, 255, 255)
        ringPaint.strokeWidth = dp(1).toFloat()
        canvas.drawCircle(centerX, centerY, radius, ringPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = (height * 0.34f).coerceAtLeast(dp(12).toFloat())
        val initial = label.firstOrNull()?.uppercaseChar()?.toString() ?: "•"
        val baseline = centerY - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(initial, centerX, baseline, textPaint)

    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()
}