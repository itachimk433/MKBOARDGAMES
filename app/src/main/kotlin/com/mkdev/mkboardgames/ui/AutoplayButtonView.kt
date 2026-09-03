package com.mkdev.mkboardgames.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import com.mkdev.mkboardgames.SoundPlayer
import kotlin.math.min

/**
 * A tactile Mancala autoplay control.
 *
 * The button uses the same rounded wood treatment as the Mancala action
 * buttons. A moving blue border marks the active autoplay state.
 */
class AutoplayButtonView(context: Context) : View(context) {

    var onAutoplayChanged: ((enabled: Boolean) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val buttonRect = RectF()
    private var enabled = false
    private var pressed = false
    private var borderAnimator: ValueAnimator? = null
    private var borderProgress = 0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.argb(175, 255, 246, 220)
    }
    private val movingBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3f * density
    }
    private val movingBorderPath = Path()
    private val movingBorderSegment = Path()
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A1714")
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    init {
        isClickable = true
        isFocusable = true
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        updateContentDescription()
        setOnClickListener { toggleAutoplay() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (150f * density).toInt()
        val desiredHeight = (46f * density).toInt()
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    fun setAutoplayEnabled(value: Boolean, animate: Boolean = true) {
        enabled = value
        updateContentDescription()
        if (value) startBorderAnimation() else stopBorderAnimation()
        if (!animate) invalidate()
    }

    private fun toggleAutoplay() {
        SoundPlayer.play("ui_click")
        val next = !enabled
        setAutoplayEnabled(next)
        onAutoplayChanged?.invoke(next)
    }

    private fun updateContentDescription() {
        contentDescription = if (enabled) "Autoplay On" else "Autoplay Off"
    }

    private fun startBorderAnimation() {
        if (borderAnimator?.isRunning == true) return
        borderAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1500L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                borderProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopBorderAnimation() {
        borderAnimator?.cancel()
        borderAnimator = null
        borderProgress = 0f
        invalidate()
    }

    private fun buttonBounds(): RectF {
        val inset = 3f * density
        val lift = if (pressed) 2f * density else 0f
        return RectF(
            inset,
            inset + lift,
            width - inset,
            height - inset + lift,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val withinButton = buttonRect.contains(event.x, event.y)
                if (pressed != withinButton) {
                    pressed = withinButton
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val shouldClick = pressed
                pressed = false
                invalidate()
                if (shouldClick) performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        borderAnimator?.cancel()
        borderAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        buttonRect.set(buttonBounds())
        val radius = min(buttonRect.height(), buttonRect.width()) * 0.18f
        val colors = intArrayOf(
            Color.parseColor("#F7D99B"),
            Color.parseColor("#C8894C"),
            Color.parseColor("#85502D"),
        )
        fillPaint.shader = LinearGradient(
            buttonRect.left,
            buttonRect.top,
            buttonRect.left,
            buttonRect.bottom,
            colors,
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        fillPaint.setShadowLayer(
            density * if (pressed) 1f else 4f,
            0f,
            density * if (pressed) 1f else 3f,
            Color.argb(170, 25, 9, 5),
        )
        canvas.drawRoundRect(buttonRect, radius, radius, fillPaint)
        fillPaint.clearShadowLayer()
        fillPaint.shader = null

        borderPaint.color = colors[2]
        canvas.drawRoundRect(buttonRect, radius, radius, borderPaint)
        val inner = RectF(
            buttonRect.left + 3f * density,
            buttonRect.top + 3f * density,
            buttonRect.right - 3f * density,
            buttonRect.bottom - 3f * density,
        )
        canvas.drawRoundRect(inner, radius * 0.78f, radius * 0.78f, highlightPaint)

        if (enabled) {
            movingBorderPaint.color = Color.parseColor("#42A5F5")
            val movingRect = RectF(buttonRect).apply {
                inset(1.5f * density, 1.5f * density)
            }
            movingBorderPath.reset()
            movingBorderPath.addRoundRect(
                movingRect,
                radius,
                radius,
                Path.Direction.CW,
            )
            val pathMeasure = PathMeasure(movingBorderPath, false)
            val pathLength = pathMeasure.length
            val segmentLength = pathLength * 0.22f
            val segmentStart = pathLength * borderProgress / 360f
            movingBorderSegment.reset()
            if (segmentStart + segmentLength <= pathLength) {
                pathMeasure.getSegment(
                    segmentStart,
                    segmentStart + segmentLength,
                    movingBorderSegment,
                    true,
                )
            } else {
                pathMeasure.getSegment(segmentStart, pathLength, movingBorderSegment, true)
                pathMeasure.getSegment(
                    0f,
                    segmentStart + segmentLength - pathLength,
                    movingBorderSegment,
                    true,
                )
            }
            canvas.drawPath(movingBorderSegment, movingBorderPaint)
        }

        labelPaint.textSize = min(width * 0.16f, height * 0.4f)
            .coerceAtLeast(12f * density)
        val autoplayLabel = if (enabled) "Autoplay On" else "Autoplay Off"
        if (labelPaint.measureText(autoplayLabel) > buttonRect.width() - 8f * density) {
            labelPaint.textSize *=
                (buttonRect.width() - 8f * density) / labelPaint.measureText(autoplayLabel)
        }
        val metrics = labelPaint.fontMetrics
        canvas.drawText(
            autoplayLabel,
            buttonRect.centerX(),
            buttonRect.centerY() - (metrics.ascent + metrics.descent) / 2f,
            labelPaint,
        )
    }
}