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
 * A compact switch for changing a board presentation.
 *
 * The host configures how many positions are available. The moving thumb and
 * accent color provide the state cue without taking space away from the game
 * HUD.
 */
class BoardStyleSwitchView(context: Context) : View(context) {

    var onStyleChanged: ((index: Int) -> Unit)? = null

    private val dp = resources.displayMetrics.density
    private val trackRect = RectF()
    private var thumbPosition = 0f
    private var styleCount = 2
    private var selectedIndex = 0
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
        contentDescription = "Board style"
        setOnClickListener { toggleStyle() }
    }

    fun setStyleCount(count: Int) {
        styleCount = count.coerceIn(2, 5)
        selectedIndex = selectedIndex.coerceIn(0, styleCount - 1)
        thumbPosition = selectedIndex / (styleCount - 1).toFloat()
        invalidate()
    }

    fun setSelectedIndex(index: Int, animate: Boolean = true) {
        selectedIndex = index.coerceIn(0, styleCount - 1)
        val target = selectedIndex / (styleCount - 1).toFloat()
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
        val nextIndex = (selectedIndex + 1) % styleCount
        setSelectedIndex(nextIndex)
        onStyleChanged?.invoke(nextIndex)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val trackWidth = 110f * dp
        val trackHeight = 30f * dp
        val left = (width - trackWidth) / 2f
        val top = (height - trackHeight) / 2f
        trackRect.set(left, top, left + trackWidth, top + trackHeight)

        val radius = trackHeight / 2f
        val palette = intArrayOf(
            Color.parseColor("#5DD6FF"),
            Color.parseColor("#FFB454"),
            Color.parseColor("#D97A45"),
            Color.parseColor("#EAE7E2"),
            Color.parseColor("#FFFFFF"),
        )
        val stateColors = if (styleCount == 2) {
            intArrayOf(palette.first(), Color.parseColor("#FF3030"))
        } else {
            palette.copyOf(styleCount)
        }

        val segmentPosition = thumbPosition * (stateColors.size - 1)
        val segmentIndex = segmentPosition.toInt().coerceIn(0, stateColors.size - 2)
        val segmentProgress = (segmentPosition - segmentIndex).coerceIn(0f, 1f)
        val thumbColor = ArgbEvaluator().evaluate(
            segmentProgress,
            stateColors[segmentIndex],
            stateColors[segmentIndex + 1],
        ) as Int

        trackPaint.color = Color.parseColor("#222A36")
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)
        canvas.drawRoundRect(trackRect, radius, radius, trackEdgePaint)

        val markerInset = 12f * dp
        val markerStep = (trackWidth - markerInset * 2f) / (stateColors.lastIndex)
        stateColors.forEachIndexed { index, color ->
            val distance = kotlin.math.abs(thumbPosition - index / (stateColors.size - 1).toFloat())
            indicatorPaint.color = Color.argb(
                ((180f * (1f - distance.coerceIn(0f, 1f))).toInt()).coerceIn(0, 255),
                Color.red(color),
                Color.green(color),
                Color.blue(color),
            )
            canvas.drawCircle(left + markerInset + markerStep * index, top + radius, 2.5f * dp, indicatorPaint)
        }

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