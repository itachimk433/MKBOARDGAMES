package com.mkdev.mkboardgames.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import com.mkdev.mkboardgames.SoundPlayer
import kotlin.math.min

/**
 * A tactile autoplay control shared by the board games.
 *
 * Non-Mancala games use the compact circular treatment from the game boards.
 * Mancala opts into its existing wood treatment. A moving blue border marks
 * the active autoplay state.
 */
class AutoplayButtonView(
    context: Context,
    private val circularStyle: Boolean = true,
) : View(context) {

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
        val desiredWidth = (if (circularStyle) 78f else 150f) * density
        val desiredHeight = (if (circularStyle) 78f else 46f) * density
        setMeasuredDimension(
            resolveSize(desiredWidth.toInt(), widthMeasureSpec),
            resolveSize(desiredHeight.toInt(), heightMeasureSpec),
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
        if (circularStyle) {
            val diameter = (min(width, height).toFloat() - 8f * density).coerceAtLeast(1f)
            val lift = if (pressed) 2f * density else 0f
            return RectF(
                (width - diameter) / 2f,
                (height - diameter) / 2f + lift,
                (width + diameter) / 2f,
                (height + diameter) / 2f + lift,
            )
        }
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
        val radius = if (circularStyle) {
            buttonRect.width() * 0.5f
        } else {
            min(buttonRect.height(), buttonRect.width()) * 0.18f
        }
        if (circularStyle) {
            val centerX = buttonRect.centerX()
            val centerY = buttonRect.centerY()
            val circleRadius = buttonRect.width() * 0.5f
            val circleColors = intArrayOf(
                Color.parseColor("#27333F"),
                Color.parseColor("#19232D"),
                Color.parseColor("#10161C"),
            )
            fillPaint.shader = RadialGradient(
                centerX - circleRadius * 0.28f,
                centerY - circleRadius * 0.35f,
                circleRadius * 1.15f,
                circleColors,
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
            fillPaint.setShadowLayer(
                density * if (pressed) 2f else 7f,
                0f,
                density * if (pressed) 1f else 4f,
                Color.argb(190, 0, 0, 0),
            )
            canvas.drawCircle(centerX, centerY, circleRadius, fillPaint)
            fillPaint.clearShadowLayer()
            fillPaint.shader = null

            borderPaint.color = Color.parseColor("#4A5968")
            canvas.drawCircle(centerX, centerY, circleRadius, borderPaint)
            highlightPaint.color = Color.argb(135, 133, 155, 177)
            canvas.drawCircle(
                centerX,
                centerY,
                circleRadius - 3f * density,
                highlightPaint,
            )
        } else {
            val woodColors = intArrayOf(
                Color.parseColor("#F7D99B"),
                Color.parseColor("#C8894C"),
                Color.parseColor("#85502D"),
            )
            fillPaint.shader = LinearGradient(
                buttonRect.left,
                buttonRect.top,
                buttonRect.left,
                buttonRect.bottom,
                woodColors,
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

            borderPaint.color = woodColors[2]
            canvas.drawRoundRect(buttonRect, radius, radius, borderPaint)
            val inner = RectF(
                buttonRect.left + 3f * density,
                buttonRect.top + 3f * density,
                buttonRect.right - 3f * density,
                buttonRect.bottom - 3f * density,
            )
            highlightPaint.color = Color.argb(175, 255, 246, 220)
            canvas.drawRoundRect(inner, radius * 0.78f, radius * 0.78f, highlightPaint)
        }

        if (enabled) {
            movingBorderPaint.color = if (circularStyle) {
                Color.parseColor("#83B7E3")
            } else {
                Color.parseColor("#42A5F5")
            }
            val movingRect = RectF(buttonRect).apply {
                inset(1.5f * density, 1.5f * density)
            }
            val movingRadius = if (circularStyle) {
                movingRect.height() * 0.5f
            } else {
                radius
            }
            movingBorderPath.reset()
            movingBorderPath.addRoundRect(
                movingRect,
                movingRadius,
                movingRadius,
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

        labelPaint.color = if (circularStyle) {
            Color.parseColor("#E5ECF3")
        } else {
            Color.parseColor("#4A1714")
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