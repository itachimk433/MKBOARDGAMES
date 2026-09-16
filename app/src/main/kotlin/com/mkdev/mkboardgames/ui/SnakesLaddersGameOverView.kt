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
    var onWatchReplay: (() -> Unit)? = null
        set(value) {
            field = value
            invalidate()
        }
    var onHome: (() -> Unit)? = null
    var winnerLabel: String = ""
        set(value) {
            field = value
            invalidate()
        }
    var winnerBaselineDp: Float = 69f
        set(value) {
            field = value
            invalidate()
        }
    private val winnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#34261B")
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D3A05F")
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val buttonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F7D99B")
        textAlign = Paint.Align.CENTER
        textSize = 11f * resources.displayMetrics.scaledDensity.coerceAtMost(3f)
    }
    private val replayRect = RectF()
    private val watchReplayRect = RectF()
    private val homeRect = RectF()
    private var pressedButton = 0

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        visibility = GONE
        isClickable = true
        contentDescription = "Game result. Play again, watch replay, or return home."
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (visibility != VISIBLE || width <= 0 || height <= 0) return

        winnerPaint.textSize = dp(18f)
        canvas.drawText(
            winnerLabel,
            width / 2f,
            dp(winnerBaselineDp),
            winnerPaint,
        )

        // Keep the same compact proportions as the HUD actions, reduced from
        // the original game-over buttons and arranged in one row.
        val showWatchReplay = onWatchReplay != null
        val horizontalMargin = dp(22f)
        val gap = dp(10f)
        val buttonCount = if (showWatchReplay) 3 else 2
        val oldGap = dp(18f)
        val oldButtonWidth = ((width - horizontalMargin * 2f - oldGap) / 2f)
        val buttonWidth = if (showWatchReplay) {
            (oldButtonWidth * 0.6f).coerceAtLeast(dp(72f))
        } else {
            oldButtonWidth
        }
        val buttonHeight = dp(37f)
        val totalWidth = buttonWidth * buttonCount + gap * (buttonCount - 1)
        val left = ((width - totalWidth) / 2f).coerceAtLeast(dp(8f))
        val top = height - dp(28f) - buttonHeight
        replayRect.set(left, top, left + buttonWidth, top + buttonHeight)
        if (showWatchReplay) {
            watchReplayRect.set(
                replayRect.right + gap,
                top,
                replayRect.right + gap + buttonWidth,
                top + buttonHeight,
            )
        } else {
            watchReplayRect.setEmpty()
        }
        val homeLeft = if (showWatchReplay) watchReplayRect.right + gap else replayRect.right + gap
        homeRect.set(
            homeLeft,
            top,
            homeLeft + buttonWidth,
            top + buttonHeight,
        )

        drawButton(canvas, replayRect, "PLAY AGAIN", pressedButton == 1)
        if (showWatchReplay) {
            drawButton(canvas, watchReplayRect, "WATCH REPLAY", pressedButton == 2)
        }
        drawButton(canvas, homeRect, "HOME", pressedButton == 3)
    }

    private fun drawButton(
        canvas: Canvas,
        rect: RectF,
        label: String,
        pressed: Boolean,
    ) {
        buttonPaint.color = Color.parseColor(if (pressed) "#45321F" else "#34261B")
        canvas.drawRoundRect(rect, dp(5f), dp(5f), buttonPaint)
        canvas.drawRoundRect(rect, dp(5f), dp(5f), borderPaint)
        val baseline = rect.centerY() - (buttonTextPaint.ascent() + buttonTextPaint.descent()) / 2f
        canvas.drawText(label, rect.centerX(), baseline, buttonTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedButton = when {
                    replayRect.contains(event.x, event.y) -> 1
                    watchReplayRect.contains(event.x, event.y) -> 2
                    homeRect.contains(event.x, event.y) -> 3
                    else -> 0
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                val selected = when {
                    replayRect.contains(event.x, event.y) -> pressedButton == 1
                    watchReplayRect.contains(event.x, event.y) -> pressedButton == 2
                    homeRect.contains(event.x, event.y) -> pressedButton == 3
                    else -> false
                }
                val action = pressedButton
                pressedButton = 0
                invalidate()
                if (selected) {
                    performClick()
                    when (action) {
                        1 -> onReplay?.invoke()
                        2 -> onWatchReplay?.invoke()
                        3 -> onHome?.invoke()
                    }
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