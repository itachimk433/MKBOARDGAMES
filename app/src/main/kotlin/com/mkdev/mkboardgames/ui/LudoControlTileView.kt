package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

class LudoControlTileView(
    context: Context,
    private val type: ControlType,
) : View(context) {
    enum class ControlType {
        MOTION,
        TAP_TO_ROLL,
    }

    var motionEnabled: Boolean = false
        set(value) {
            field = value
            contentDescription = if (value) {
                "Motion dice enabled. Hold to disable."
            } else {
                "Motion dice disabled. Hold to enable."
            }
            invalidate()
        }

    var onMotionToggle: ((Boolean) -> Unit)? = null
    var onTap: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var touchDown = false
    private var longPressTriggered = false
    private var pressed = false
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(166, 174, 184)
        textAlign = Paint.Align.CENTER
    }

    private val longPressRunnable = Runnable {
        if (touchDown && type == ControlType.MOTION) {
            longPressTriggered = true
            motionEnabled = !motionEnabled
            onMotionToggle?.invoke(motionEnabled)
            performClick()
        }
    }

    init {
        isClickable = true
        contentDescription = if (type == ControlType.TAP_TO_ROLL) "Tap to roll" else "Motion dice control"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val radius = 12f * density
        tilePaint.color = if (pressed) Color.rgb(44, 52, 62) else Color.rgb(25, 31, 38)
        tilePaint.style = Paint.Style.FILL
        tilePaint.setShadowLayer(4f * density, 0f, 2f * density, Color.argb(110, 0, 0, 0))
        canvas.drawRoundRect(
            RectF(
                2f * density,
                4f * density,
                width - 2f * density,
                height - 2f * density,
            ),
            radius,
            radius,
            tilePaint,
        )
        tilePaint.clearShadowLayer()
        tilePaint.color = Color.rgb(111, 121, 133)
        tilePaint.style = Paint.Style.STROKE
        tilePaint.strokeWidth = 1.5f * density
        canvas.drawRoundRect(
            RectF(
                3f * density,
                5f * density,
                width - 3f * density,
                height - 3f * density,
            ),
            radius,
            radius,
            tilePaint,
        )
        tilePaint.style = Paint.Style.FILL

        if (type == ControlType.MOTION) {
            iconPaint.color = if (motionEnabled) Color.rgb(220, 225, 232) else Color.rgb(123, 133, 145)
            canvas.drawCircle(width / 2f, 28f * density, 8f * density, iconPaint)
            iconPaint.color = Color.rgb(41, 48, 57)
            canvas.drawCircle(width / 2f, 28f * density, 3f * density, iconPaint)
            textPaint.color = Color.rgb(228, 232, 237)
            textPaint.textSize = 15f * density
            canvas.drawText(
                "Motion: ${if (motionEnabled) "ON" else "OFF"}",
                width / 2f,
                70f * density,
                textPaint,
            )
            secondaryPaint.textSize = 12f * density
            canvas.drawText("(Hold to change)", width / 2f, 89f * density, secondaryPaint)
            iconPaint.color = Color.rgb(203, 209, 217)
            repeat(3) { index ->
                canvas.drawCircle(
                    width / 2f - 9f * density + index * 9f * density,
                    111f * density,
                    2f * density,
                    iconPaint,
                )
            }
        } else {
            textPaint.color = Color.rgb(228, 232, 237)
            textPaint.textSize = 16f * density
            canvas.drawText("TAP TO ROLL", width / 2f, 77f * density, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDown = true
                pressed = true
                longPressTriggered = false
                if (type == ControlType.MOTION) {
                    handler.postDelayed(longPressRunnable, 520L)
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                touchDown = false
                pressed = false
                handler.removeCallbacks(longPressRunnable)
                if (!longPressTriggered && type == ControlType.TAP_TO_ROLL) {
                    performClick()
                    onTap?.invoke()
                }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                touchDown = false
                pressed = false
                handler.removeCallbacks(longPressRunnable)
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(longPressRunnable)
        super.onDetachedFromWindow()
    }
}