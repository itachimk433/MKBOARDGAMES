package com.mkdev.mkboardgames.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.mkdev.mkboardgames.GameMode
import com.mkdev.mkboardgames.SoundPlayer
import kotlin.math.min

/**
 * The first screen shown when the app opens.
 *
 * This screen only chooses the ruleset context. The app logo and Settings
 * belong to the game catalogue and are intentionally not rendered here.
 */
class ModeSelectionView(context: Context) : View(context) {

    var onModeSelected: ((GameMode) -> Unit)? = null

    private val unit = resources.displayMetrics.density.coerceAtLeast(1f)
    private val textScale = resources.displayMetrics.scaledDensity.coerceAtMost(2f)
    private val normalRect = RectF()
    private val irregularRect = RectF()
    private var pressedMode: GameMode? = null
    private var loadingMode: GameMode? = null
    private var loadingAngle = 0f
    private var loadingAnimator: ValueAnimator? = null

    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#121212")
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 28f * textScale
        letterSpacing = 0.06f
    }
    private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F7D99B")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 11f * textScale
        letterSpacing = 0.12f
    }
    private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#63301F")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 21f * textScale
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A1714")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 14f * textScale
    }
    private val loadingRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A1714")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * unit
        strokeCap = Paint.Cap.ROUND
    }

    init {
        isClickable = true
        SoundPlayer.init(context)
    }

    override fun onDetachedFromWindow() {
        loadingAnimator?.cancel()
        loadingAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        val buttonWidth = min(width - 48f * unit, 360f * unit)
        val left = (width - buttonWidth) / 2f
        val buttonHeight = 82f * unit
        val gap = 12f * unit
        val totalHeight = buttonHeight * 2f + gap
        val firstTop = (height - totalHeight) / 2f
        normalRect.set(left, firstTop, left + buttonWidth, firstTop + buttonHeight)
        irregularRect.set(
            left,
            normalRect.bottom + gap,
            left + buttonWidth,
            normalRect.bottom + gap + buttonHeight,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val centerX = width / 2f
        canvas.drawText("MK BOARD GAMES", centerX, height * 0.17f, titlePaint)
        canvas.drawText("SELECT MODE", centerX, height * 0.285f, sectionPaint)

        drawModeButton(canvas, normalRect, GameMode.NORMAL, "♟️", "Play")
        drawModeButton(canvas, irregularRect, GameMode.IRREGULAR, "♟️♟️", "Play (IRREGULAR MODE)")
    }

    private fun drawModeButton(
        canvas: Canvas,
        rect: RectF,
        mode: GameMode,
        symbol: String,
        label: String,
    ) {
        val pressed = pressedMode == mode
        drawChessWoodButton(canvas, rect, pressed, unit)
        val offset = if (pressed) 2f * unit else 0f
        canvas.drawText(symbol, rect.centerX(), rect.top + 27f * unit + offset, symbolPaint)
        canvas.drawText(label, rect.centerX(), rect.top + 58f * unit + offset, labelPaint)
        if (loadingMode == mode) drawLoadingRing(canvas, rect)
    }

    private fun drawLoadingRing(canvas: Canvas, rect: RectF) {
        val radius = 8f * unit
        val centerX = rect.right - 17f * unit
        val centerY = rect.top + 17f * unit
        canvas.drawArc(
            RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius),
            loadingAngle,
            285f,
            false,
            loadingRingPaint,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (loadingMode != null) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedMode = when {
                    normalRect.contains(event.x, event.y) -> GameMode.NORMAL
                    irregularRect.contains(event.x, event.y) -> GameMode.IRREGULAR
                    else -> null
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val current = pressedMode
                if (current != null) {
                    val rect = if (current == GameMode.NORMAL) normalRect else irregularRect
                    if (!rect.contains(event.x, event.y)) {
                        pressedMode = null
                        invalidate()
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val selected = pressedMode
                val rect = when (selected) {
                    GameMode.NORMAL -> normalRect
                    GameMode.IRREGULAR -> irregularRect
                    null -> null
                }
                pressedMode = null
                invalidate()
                if (selected != null && rect?.contains(event.x, event.y) == true) {
                    SoundPlayer.play("ui_click")
                    loadingMode = selected
                    loadingAnimator?.cancel()
                    loadingAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                        duration = 700L
                        repeatCount = ValueAnimator.INFINITE
                        addUpdateListener {
                            loadingAngle = it.animatedValue as Float
                            invalidate()
                        }
                        start()
                    }
                    postDelayed({
                        if (loadingMode == selected) onModeSelected?.invoke(selected)
                    }, 260L)
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedMode = null
                invalidate()
                return true
            }
        }
        return true
    }
}