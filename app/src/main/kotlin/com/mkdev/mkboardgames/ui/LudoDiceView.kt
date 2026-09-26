package com.mkdev.mkboardgames.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class MotionDiceDirection {
    UP, LEFT, RIGHT, TOP_LEFT, TOP_RIGHT
}

/**
 * A compact, flat Ludo die for the four player side rails.
 *
 * It deliberately has no opaque panel or 3D surface: the board remains visible
 * around it and each die uses its player's colour as the only decoration.
 */
class LudoDiceView(context: Context) : View(context) {
    var value: Int = 1
    var accentColor: Int = Color.WHITE
    var facesOppositeSide: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var isRolling: Boolean = false
        private set
    var onRoll: (() -> Unit)? = null

    private var animator: ValueAnimator? = null
    private var rollGeneration = 0
    private var rotation = 0f
    private var scale = 1f
    private var animatorPausedForHost = false

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 0, 0, 0)
    }
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val pipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(25, 32, 40)
    }

    init {
        isClickable = true
        setBackgroundColor(Color.TRANSPARENT)
        elevation = 3f * resources.displayMetrics.density
    }

    fun rollTo(
        nextValue: Int,
        motionDirection: MotionDiceDirection = MotionDiceDirection.UP,
        onFinished: () -> Unit,
    ) {
        val generation = ++rollGeneration
        animator?.cancel()
        isRolling = true
        val targetValue = nextValue.coerceIn(1, 6)
        val startRotation = rotation
        val directionSign = when (motionDirection) {
            MotionDiceDirection.LEFT, MotionDiceDirection.TOP_LEFT -> -1f
            MotionDiceDirection.RIGHT, MotionDiceDirection.TOP_RIGHT -> 1f
            MotionDiceDirection.UP -> if (Random.nextBoolean()) 1f else -1f
        }

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 546L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val progress = it.animatedFraction
                val eased = progress * progress * (3f - 2f * progress)
                rotation = startRotation + directionSign * 360f * 2.5f * eased
                scale = 1f + sin(progress * Math.PI).toFloat() * 0.08f
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (generation != rollGeneration) return
                    value = targetValue
                    rotation = 0f
                    scale = 1f
                    isRolling = false
                    animator = null
                    invalidate()
                    onFinished()
                }
            })
            start()
        }
    }

    fun cancelRoll() {
        rollGeneration++
        animator?.cancel()
        animator = null
        animatorPausedForHost = false
        isRolling = false
        rotation = 0f
        scale = 1f
        invalidate()
    }

    fun onHostPause() {
        if (isRolling && animator != null) {
            animator?.pause()
            animatorPausedForHost = true
        }
    }

    fun onHostResume() {
        if (animatorPausedForHost) {
            animatorPausedForHost = false
            animator?.resume()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val size = min(width, height).toFloat()
        val centerX = width / 2f
        val centerY = height / 2f
        val half = size * 0.34f * scale
        val radius = size * 0.18f

        canvas.save()
        if (facesOppositeSide) canvas.rotate(180f, centerX, centerY)
        canvas.rotate(rotation, centerX, centerY)
        val shadow = RectF(
            centerX - half + density,
            centerY - half + 2f * density,
            centerX + half + density,
            centerY + half + 2f * density,
        )
        canvas.drawRoundRect(shadow, radius, radius, shadowPaint)

        val face = RectF(centerX - half, centerY - half, centerX + half, centerY + half)
        canvas.drawRoundRect(face, radius, radius, facePaint)
        borderPaint.color = accentColor
        borderPaint.strokeWidth = 2.5f * density
        canvas.drawRoundRect(face, radius, radius, borderPaint)

        val pipRadius = size * 0.065f
        val offset = half * 0.52f
        val positions = when (value.coerceIn(1, 6)) {
            1 -> listOf(0f to 0f)
            2 -> listOf(-1f to -1f, 1f to 1f)
            3 -> listOf(-1f to -1f, 0f to 0f, 1f to 1f)
            4 -> listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)
            5 -> listOf(-1f to -1f, 1f to -1f, 0f to 0f, -1f to 1f, 1f to 1f)
            else -> listOf(
                -1f to -1f, 1f to -1f, -1f to 0f,
                1f to 0f, -1f to 1f, 1f to 1f,
            )
        }
        positions.forEach { (x, y) ->
            canvas.drawCircle(centerX + x * offset, centerY + y * offset, pipRadius, pipPaint)
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP && !isRolling) {
            performClick()
            onRoll?.invoke()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        cancelRoll()
        super.onDetachedFromWindow()
    }
}