package com.mkdev.mkboardgames.ui

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

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 28f * textScale
        letterSpacing = 0.06f
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(205, 220, 235, 244)
        textAlign = Paint.Align.CENTER
        textSize = 13f * textScale
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

    init {
        isClickable = true
        SoundPlayer.init(context)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        val buttonWidth = min(width - 48f * unit, 360f * unit)
        val left = (width - buttonWidth) / 2f
        val buttonHeight = 82f * unit
        val gap = 12f * unit
        val firstTop = height * 0.34f
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
        drawChessAtmosphere(canvas, width.toFloat(), height.toFloat(), unit, rounded = false)

        val centerX = width / 2f
        canvas.drawText("MK BOARD GAMES", centerX, height * 0.17f, titlePaint)
        canvas.drawText("Choose how you want to play", centerX, height * 0.205f, subtitlePaint)
        canvas.drawText("SELECT MODE", centerX, height * 0.285f, sectionPaint)

        drawModeButton(canvas, normalRect, GameMode.NORMAL, "♟", "Play")
        drawModeButton(canvas, irregularRect, GameMode.IRREGULAR, "✦", "Play (IRREGULAR MODE)")
    }

    private fun drawModeButton(
        canvas: Canvas,
        rect: RectF,
        mode: GameMode,
        symbol: String,
        label: String,
    ) {
        drawChessWoodButton(canvas, rect, pressedMode == mode, unit)
        val offset = if (pressedMode == mode) 2f * unit else 0f
        canvas.drawText(symbol, rect.centerX(), rect.top + 27f * unit + offset, symbolPaint)
        canvas.drawText(label, rect.centerX(), rect.top + 58f * unit + offset, labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
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
                    onModeSelected?.invoke(selected)
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