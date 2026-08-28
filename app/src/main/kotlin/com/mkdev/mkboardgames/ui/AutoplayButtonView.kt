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
import android.view.animation.OvershootInterpolator
import com.mkdev.mkboardgames.SoundPlayer

/**
 * A tactile Chess autoplay control.
 *
 * Green means the player's pieces remain under manual control. Red means
 * autoplay is active and the configured Chess AI can make the player's moves.
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

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * dp
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
        contentDescription = "Autoplay"
        setOnClickListener { toggleAutoplay() }
    }

    fun setAutoplayEnabled(value: Boolean, animate: Boolean = true) {
        enabled = value
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

    override fun onDetachedFromWindow() {
        stateAnimator?.cancel()
        pressAnimator?.cancel()
        stateAnimator = null
        pressAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val buttonWidth = 146f * dp
        val buttonHeight = 36f * dp
        buttonRect.set(
            (width - buttonWidth) / 2f,
            (height - buttonHeight) / 2f,
            (width + buttonWidth) / 2f,
            (height + buttonHeight) / 2f,
        )

        val green = Color.parseColor("#55E28C")
        val red = Color.parseColor("#FF5F6D")
        val accent = ArgbEvaluator().evaluate(stateProgress, green, red) as Int
        val radius = buttonHeight / 2f

        canvas.save()
        canvas.scale(pressScale, pressScale, buttonRect.centerX(), buttonRect.centerY())

        // Layered translucent edges create a soft light around the button
        // without requiring a software blur layer on older Android versions.
        glowPaint.color = Color.argb(24, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(
            buttonRect.left - 4f * dp,
            buttonRect.top - 4f * dp,
            buttonRect.right + 4f * dp,
            buttonRect.bottom + 4f * dp,
            radius + 4f * dp,
            radius + 4f * dp,
            glowPaint,
        )
        glowPaint.color = Color.argb(44, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(
            buttonRect.left - 1.5f * dp,
            buttonRect.top - 1.5f * dp,
            buttonRect.right + 1.5f * dp,
            buttonRect.bottom + 1.5f * dp,
            radius + 1.5f * dp,
            radius + 1.5f * dp,
            glowPaint,
        )

        fillPaint.color = Color.parseColor("#1E252C")
        canvas.drawRoundRect(buttonRect, radius, radius, fillPaint)
        edgePaint.color = accent
        canvas.drawRoundRect(buttonRect, radius, radius, edgePaint)

        textPaint.color = Color.WHITE
        canvas.drawText(
            "Autoplay",
            buttonRect.centerX(),
            buttonRect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f,
            textPaint,
        )
        canvas.restore()
    }
}