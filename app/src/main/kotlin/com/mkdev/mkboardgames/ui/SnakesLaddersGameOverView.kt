package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View

/**
 * In-board game-over controls. The board remains visible while the title and
 * actions sit in the open top and bottom areas of the themed artwork.
 */
class SnakesLaddersGameOverView(context: Context) : View(context) {
    var onReplay: (() -> Unit)? = null
    var onHome: (() -> Unit)? = null
    var winnerLabel: String = ""
        set(value) {
            field = value
            invalidate()
        }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val winnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val buttonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val replayRect = RectF()
    private val homeRect = RectF()
    private var pressedButton = 0

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        visibility = GONE
        isClickable = true
        contentDescription = "Game over. Replay or return home."
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (visibility != VISIBLE || width <= 0 || height <= 0) return

        titlePaint.textSize = dp(28f)
        titlePaint.setShadowLayer(dp(7f), 0f, dp(2f), Color.argb(220, 0, 0, 0))
        canvas.drawText(
            "GAME OVER",
            width / 2f,
            dp(38f),
            titlePaint,
        )
        titlePaint.clearShadowLayer()

        winnerPaint.textSize = dp(18f)
        winnerPaint.setShadowLayer(dp(5f), 0f, dp(2f), Color.argb(220, 0, 0, 0))
        canvas.drawText(
            winnerLabel,
            width / 2f,
            dp(69f),
            winnerPaint,
        )
        winnerPaint.clearShadowLayer()

        val horizontalMargin = dp(22f)
        val gap = dp(18f)
        val buttonHeight = dp(62f)
        val buttonWidth = ((width - horizontalMargin * 2f - gap) / 2f)
            .coerceAtLeast(dp(112f))
        val top = height - dp(22f) - buttonHeight
        replayRect.set(horizontalMargin, top, horizontalMargin + buttonWidth, top + buttonHeight)
        homeRect.set(
            width - horizontalMargin - buttonWidth,
            top,
            width - horizontalMargin,
            top + buttonHeight,
        )

        drawButton(
            canvas,
            replayRect,
            Color.rgb(203, 149, 59),
            "REPLAY",
            pressedButton == 1,
        )
        drawButton(
            canvas,
            homeRect,
            Color.rgb(181, 78, 79),
            "HOME",
            pressedButton == 2,
        )
    }

    private fun drawButton(
        canvas: Canvas,
        rect: RectF,
        color: Int,
        label: String,
        pressed: Boolean,
    ) {
        buttonPaint.color = Color.argb(if (pressed) 245 else 220, Color.red(color), Color.green(color), Color.blue(color))
        buttonPaint.setShadowLayer(dp(if (pressed) 2f else 6f), 0f, dp(2f), Color.argb(160, 0, 0, 0))
        canvas.drawRoundRect(rect, dp(18f), dp(18f), buttonPaint)
        buttonPaint.clearShadowLayer()

        borderPaint.color = Color.argb(235, 255, 255, 255)
        canvas.drawRoundRect(rect, dp(18f), dp(18f), borderPaint)

        buttonTextPaint.textSize = dp(17f)
        buttonTextPaint.setShadowLayer(dp(3f), 0f, dp(1f), Color.argb(190, 0, 0, 0))
        val baseline = rect.centerY() - (buttonTextPaint.ascent() + buttonTextPaint.descent()) / 2f
        canvas.drawText(label, rect.centerX(), baseline, buttonTextPaint)
        buttonTextPaint.clearShadowLayer()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedButton = when {
                    replayRect.contains(event.x, event.y) -> 1
                    homeRect.contains(event.x, event.y) -> 2
                    else -> 0
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                val selected = when {
                    replayRect.contains(event.x, event.y) -> pressedButton == 1
                    homeRect.contains(event.x, event.y) -> pressedButton == 2
                    else -> false
                }
                val action = pressedButton
                pressedButton = 0
                invalidate()
                if (selected) {
                    performClick()
                    if (action == 1) onReplay?.invoke() else onHome?.invoke()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedButton = 0
                invalidate()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density
}