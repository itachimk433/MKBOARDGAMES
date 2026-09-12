package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.roundToInt

/**
 * Compact player identity badge used beside each Ludo die.
 *
 * The badge is deliberately drawn instead of assembled from several TextViews
 * so the four corner controls keep the same proportions on small screens.
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

    var avatarOnEnd: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var isActive: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val avatarRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        minimumHeight = dp(42)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val density = resources.displayMetrics.density
        val horizontalInset = dp(2).toFloat()
        val verticalInset = dp(4).toFloat()
        val radius = dp(12).toFloat()
        val rect = RectF(
            horizontalInset,
            verticalInset,
            width - horizontalInset,
            height - verticalInset,
        )

        backgroundPaint.style = Paint.Style.FILL
        backgroundPaint.color = Color.argb(238, 10, 18, 27)
        backgroundPaint.clearShadowLayer()
        if (isActive) {
            backgroundPaint.setShadowLayer(
                dp(9).toFloat(),
                0f,
                0f,
                Color.argb(220, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
            )
        }
        canvas.drawRoundRect(rect, radius, radius, backgroundPaint)

        borderPaint.color = if (isActive) {
            Color.WHITE
        } else {
            Color.argb(190, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        }
        borderPaint.strokeWidth = if (isActive) dp(2).toFloat() else dp(1).toFloat()
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        val avatarRadius = (height * 0.31f).coerceAtLeast(dp(11).toFloat())
        val avatarX = if (avatarOnEnd) {
            width - dp(19).toFloat()
        } else {
            dp(19).toFloat()
        }
        val avatarY = height / 2f

        avatarPaint.color = accentColor
        avatarPaint.style = Paint.Style.FILL
        avatarPaint.clearShadowLayer()
        if (isActive) {
            avatarPaint.setShadowLayer(
                dp(5).toFloat(),
                0f,
                0f,
                Color.argb(220, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
            )
        }
        canvas.drawCircle(avatarX, avatarY, avatarRadius, avatarPaint)

        avatarRingPaint.color = Color.argb(235, 255, 255, 255)
        avatarRingPaint.strokeWidth = dp(1).toFloat()
        canvas.drawCircle(avatarX, avatarY, avatarRadius, avatarRingPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = (height * 0.32f).coerceAtLeast(dp(10).toFloat())
        val initial = label.firstOrNull()?.uppercaseChar()?.toString() ?: "•"
        val textOffset = (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(initial, avatarX, avatarY - textOffset, textPaint)

        textPaint.textSize = (height * 0.25f).coerceAtLeast(dp(9).toFloat())
        val labelX = if (avatarOnEnd) {
            dp(26).toFloat()
        } else {
            width - dp(27).toFloat()
        }
        textPaint.textAlign = if (avatarOnEnd) Paint.Align.LEFT else Paint.Align.RIGHT
        canvas.drawText(label, labelX, avatarY - textOffset, textPaint)
        textPaint.textAlign = Paint.Align.CENTER

        if (isActive) {
            avatarPaint.clearShadowLayer()
            avatarPaint.color = Color.WHITE
            canvas.drawCircle(
                if (avatarOnEnd) dp(9).toFloat() else width - dp(9).toFloat(),
                dp(9).toFloat(),
                dp(3).toFloat(),
                avatarPaint,
            )
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()
}