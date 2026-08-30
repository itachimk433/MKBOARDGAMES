package com.mkdev.mkboardgames.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import com.mkdev.mkboardgames.SoundPlayer

/**
 * A tactile Chess autoplay control.
 *
 * The button stays neutral while the player's pieces remain under manual
 * control. A moving blue border marks the active autoplay state.
 */
class AutoplayButtonView(context: Context) : View(context) {

    var onAutoplayChanged: ((enabled: Boolean) -> Unit)? = null

    private val dp = resources.displayMetrics.density
    private val buttonRect = RectF()
    private var enabled = false
    private var stateProgress = 0f
    private var pressScale = 1f
    private var stateAnimator: ValueAnimator? = null
    private var pressAnimator: ValueAnimator? = null
    private var borderAnimator: ValueAnimator? = null
    private var borderProgress = 0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * dp
    }
    private val movingBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3.2f * dp
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT,
            android.graphics.Typeface.BOLD,
        )
        textSize = 13f * resources.displayMetrics.scaledDensity.coerceAtMost(3f)
    }

    init {
        isClickable = true
        isFocusable = true
        updateContentDescription()
        setOnClickListener { toggleAutoplay() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (150f * dp).toInt()
        val desiredHeight = (76f * dp).toInt()
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    fun setAutoplayEnabled(value: Boolean, animate: Boolean = true) {
        enabled = value
        updateContentDescription()
        if (value) startBorderAnimation() else stopBorderAnimation()
        val target = if (value) 1f else 0f
        stateAnimator?.cancel()
        if (!animate) {
            stateProgress = target
            invalidate()
            return
        }
        stateAnimator = ValueAnimator.ofFloat(stateProgress, target).apply {
            duration = 240L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                stateProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun toggleAutoplay() {
        SoundPlayer.play("ui_click")
        val next = !enabled
        setAutoplayEnabled(next)
        runClickAnimation()
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

    private fun runClickAnimation() {
        pressAnimator?.cancel()
        pressAnimator = ValueAnimator.ofFloat(1f, 0.91f, 1.04f, 1f).apply {
            duration = 360L
            interpolator = OvershootInterpolator(1.1f)
            addUpdateListener {
                pressScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val buttonDiameter = minOf(width.toFloat(), height.toFloat()) - 12f * dp
        val buttonRect = RectF(
            (width - buttonDiameter) / 2f,
            (height - buttonDiameter) / 2f,
            (width + buttonDiameter) / 2f,
            (height + buttonDiameter) / 2f,
        )
        val radius = buttonDiameter / 2f
        val withinCircle = kotlin.math.hypot(
            event.x - buttonRect.centerX(),
            event.y - buttonRect.centerY(),
        ) <= radius

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return if (withinCircle) {
                isPressed = true
                true
            } else {
                false
            }
            MotionEvent.ACTION_MOVE -> return withinCircle && isPressed
            MotionEvent.ACTION_UP -> {
                if (!withinCircle || !isPressed) {
                    isPressed = false
                    return false
                }
                isPressed = false
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                return false
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        stateAnimator?.cancel()
        pressAnimator?.cancel()
        borderAnimator?.cancel()
        stateAnimator = null
        pressAnimator = null
        borderAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val buttonDiameter = minOf(width.toFloat(), height.toFloat()) - 12f * dp
        buttonRect.set(
            (width - buttonDiameter) / 2f,
            (height - buttonDiameter) / 2f,
            (width + buttonDiameter) / 2f,
            (height + buttonDiameter) / 2f,
        )

        val offAccent = Color.parseColor("#6B7785")
        val blueAccent = Color.parseColor("#42A5F5")
        val accent = ArgbEvaluator().evaluate(stateProgress, offAccent, blueAccent) as Int
        val radius = buttonDiameter / 2f

        canvas.save()
        canvas.scale(pressScale, pressScale, buttonRect.centerX(), buttonRect.centerY())

        // Layered translucent edges create a soft light around the button
        // without requiring a software blur layer on older Android versions.
        glowPaint.color = Color.argb(24, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawCircle(buttonRect.centerX(), buttonRect.centerY(), radius + 4f * dp, glowPaint)
        glowPaint.color = Color.argb(44, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawCircle(buttonRect.centerX(), buttonRect.centerY(), radius + 1.5f * dp, glowPaint)

        fillPaint.color = Color.parseColor("#1E252C")
        canvas.drawCircle(buttonRect.centerX(), buttonRect.centerY(), radius, fillPaint)
        edgePaint.color = accent
        canvas.drawCircle(buttonRect.centerX(), buttonRect.centerY(), radius, edgePaint)

        if (enabled) {
            movingBorderPaint.color = blueAccent
            val movingRect = RectF(buttonRect).apply { inset(1.5f * dp, 1.5f * dp) }
            canvas.drawArc(movingRect, borderProgress - 42f, 112f, false, movingBorderPaint)
        }

        textPaint.color = Color.WHITE
        textPaint.textSize = minOf(
            11f * resources.displayMetrics.scaledDensity.coerceAtMost(3f),
            buttonDiameter * 0.14f,
        )
        val autoplayLabel = if (enabled) "Autoplay On" else "Autoplay Off"
        if (textPaint.measureText(autoplayLabel) > buttonDiameter - 8f * dp) {
            textPaint.textSize *= (buttonDiameter - 8f * dp) / textPaint.measureText(autoplayLabel)
        }
        canvas.drawText(
            autoplayLabel,
            buttonRect.centerX(),
            buttonRect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f,
            textPaint,
        )
        canvas.restore()
    }
}