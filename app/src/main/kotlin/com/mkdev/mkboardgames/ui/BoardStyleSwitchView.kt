package com.mkdev.mkboardgames.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.mkdev.mkboardgames.SoundPlayer

/**
 * A compact, nameless switch for changing the Chess board presentation.
 *
 * The left position is the generated canvas board and the right position is
 * the photographed board. The moving thumb and accent color provide the state
 * cue without taking space away from the game HUD.
 */
class BoardStyleSwitchView(context: Context) : View(context) {

    var onStyleChanged: ((useCanvasBoard: Boolean) -> Unit)? = null

    private val dp = resources.displayMetrics.density
    private val trackRect = RectF()
    private var thumbPosition = 0f
    private var canvasSelected = true
    private var animator: ValueAnimator? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp
        color = Color.parseColor("#3F4B5D")
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(105, 255, 255, 255)
    }
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "Chess board style"
        setOnClickListener { toggleStyle() }
    }

    fun setCanvasSelected(selected: Boolean, animate: Boolean = true) {
        canvasSelected = selected
        val target = if (selected) 0f else 1f
        animator?.cancel()
        if (!animate) {
            thumbPosition = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(thumbPosition, target).apply {
            duration = 260L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                thumbPosition = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun toggleStyle() {
        SoundPlayer.play("ui_click")
        val next = !canvasSelected
        setCanvasSelected(next)
        onStyleChanged?.invoke(next)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val trackWidth = 74f * dp
        val trackHeight = 32f * dp
        val left = (width - trackWidth) / 2f
        val top = (height - trackHeight) / 2f
        trackRect.set(left, top, left + trackWidth, top + trackHeight)

        val radius = trackHeight / 2f
        val canvasColor = Color.parseColor("#5DD6FF")
        val boardColor = Color.parseColor("#FFB454")
        val thumbColor = ArgbEvaluator().evaluate(thumbPosition, canvasColor, boardColor) as Int

        trackPaint.color = Color.parseColor("#222A36")
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)
        canvas.drawRoundRect(trackRect, radius, radius, trackEdgePaint)

        // Small, nameless state markers keep the control understandable even
        // when the thumb is between positions during its transition.
        indicatorPaint.color = Color.argb(
            (175 * (1f - thumbPosition)).toInt(),
            Color.red(canvasColor),
            Color.green(canvasColor),
            Color.blue(canvasColor),
        )
        canvas.drawCircle(left + 13f * dp, top + radius, 2.5f * dp, indicatorPaint)
        indicatorPaint.color = Color.argb(
            (175 * thumbPosition).toInt(),
            Color.red(boardColor),
            Color.green(boardColor),
            Color.blue(boardColor),
        )
        canvas.drawCircle(left + trackWidth - 13f * dp, top + radius, 2.5f * dp, indicatorPaint)

        val thumbRadius = 11f * dp
        val thumbCenterX = left + 16f * dp + thumbPosition * (trackWidth - 32f * dp)
        val thumbCenterY = top + radius
        thumbPaint.color = thumbColor
        canvas.drawCircle(thumbCenterX, thumbCenterY, thumbRadius + 1.5f * dp, thumbPaint)
        thumbPaint.color = Color.argb(235, 255, 255, 255)
        canvas.drawCircle(thumbCenterX, thumbCenterY, thumbRadius - 1.5f * dp, thumbPaint)
        canvas.drawCircle(
            thumbCenterX - 2f * dp,
            thumbCenterY - 2f * dp,
            2.5f * dp,
            thumbHighlightPaint,
        )
    }
}