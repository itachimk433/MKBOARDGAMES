package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.mkdev.mkboardgames.SoundPlayer
import kotlin.math.min

/**
 * The finished-Chess actions use the same card renderer and typography as the
 * mode-selection cards, arranged as two cards above one full-width card.
 */
class ChessResultActionsView(context: Context) : View(context) {

    var onPlayAgain: (() -> Unit)? = null
    var onMainMenu: (() -> Unit)? = null
    var onWatchReplay: (() -> Unit)? = null

    private val unit = resources.displayMetrics.density.coerceAtLeast(1f)
    private val textScale = resources.displayMetrics.scaledDensity.coerceAtMost(2f)
    private val cardRenderer = BrownWoodCardRenderer(unit)
    private val cards = Array(3) { RectF() }
    private var pressedCard: Int? = null

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 23f * textScale
        setShadowLayer(3f * unit, 0f, 2f * unit, Color.argb(220, 0, 0, 0))
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 12f * textScale
        setShadowLayer(2f * unit, 0f, 1f * unit, Color.argb(230, 0, 0, 0))
    }
    private val descriptionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BDBDBD")
        textAlign = Paint.Align.CENTER
        textSize = 8f * textScale
        setShadowLayer(1.5f * unit, 0f, 1f * unit, Color.argb(210, 0, 0, 0))
    }

    private val labels = arrayOf("Play Again", "Main Menu", "Watch Replay")
    private val descriptions = arrayOf(
        "Start a fresh game",
        "Choose another match",
        "Review every move",
    )
    private val icons = arrayOf("↻", "⌂", "▶")

    init {
        isClickable = true
        visibility = GONE
        contentDescription = "Chess game over actions"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cardHeight = 136f / 3f * 1.1f * unit
        val gap = 10f * unit
        val desiredHeight = (cardHeight * 2f + gap).toInt()
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        val gap = 10f * unit
        val cardHeight = 136f / 3f * 1.1f * unit
        val cardWidth = min(width - 48f * unit, 360f * unit)
        val left = (width - cardWidth) / 2f
        val halfWidth = (cardWidth - gap) / 2f
        val top = (height - (cardHeight * 2f + gap)) / 2f

        cards[0].set(left, top, left + halfWidth, top + cardHeight)
        cards[1].set(
            left + halfWidth + gap,
            top,
            left + cardWidth,
            top + cardHeight,
        )
        cards[2].set(
            left,
            top + cardHeight + gap,
            left + cardWidth,
            top + cardHeight * 2f + gap,
        )
    }

    override fun onDraw(canvas: Canvas) {
        cards.forEachIndexed { index, rect ->
            cardRenderer.draw(canvas, rect, pressedCard == index)
            val iconCenterX = rect.left + 32f * unit
            val textCenterX = rect.left + 32f * unit + (rect.width() - 32f * unit) / 2f
            canvas.drawText(icons[index], iconCenterX, rect.centerY() + 8f * unit, iconPaint)
            canvas.drawText(labels[index], textCenterX, rect.centerY() + 1f * unit, titlePaint)
            canvas.drawText(
                descriptions[index],
                textCenterX,
                rect.centerY() + 14f * unit,
                descriptionPaint,
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedCard = cards.indexOfFirst { it.contains(event.x, event.y) }
                    .takeIf { it >= 0 }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (pressedCard != null && !cards[pressedCard!!].contains(event.x, event.y)) {
                    pressedCard = null
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val selected = pressedCard
                val activated = selected != null && cards[selected].contains(event.x, event.y)
                pressedCard = null
                invalidate()
                if (activated) {
                    performClick()
                    SoundPlayer.play("ui_click")
                    when (selected) {
                        0 -> onPlayAgain?.invoke()
                        1 -> onMainMenu?.invoke()
                        2 -> onWatchReplay?.invoke()
                    }
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedCard = null
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
}