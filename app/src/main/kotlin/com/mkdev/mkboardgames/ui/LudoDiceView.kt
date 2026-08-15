package com.mkdev.mkboardgames.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin

/**
 * A native, lightweight 3D presentation for the supplied dice.gltf model.
 * The model is kept in assets as the source art, while this view draws the
 * rounded die directly on Canvas so the roll remains smooth on older Android
 * devices without requiring a separate 3D runtime.
 */
class LudoDiceView(context: Context) : View(context) {
    var value: Int = 1
    var isRolling: Boolean = false
        private set
    var onRoll: (() -> Unit)? = null

    private var spin = 0f
    private var animator: ValueAnimator? = null
    private val modelAssetAvailable = try {
        context.assets.open("dice.gltf").use { it.read() >= 0 }
    } catch (_: Exception) {
        false
    }
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(200, 255, 255, 255)
        strokeWidth = 2f
    }
    private val pipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#182029") }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAB8C6")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#121A21"))
        labelPaint.textSize = 12f * resources.displayMetrics.scaledDensity.coerceAtMost(3f)
        canvas.drawText(if (isRolling) "Rolling..." else "Tap to roll", width / 2f, 22f * resources.displayMetrics.density, labelPaint)

        val size = minOf(width, height) * 0.48f
        val cx = width / 2f
        val cy = height * 0.58f
        val angle = spin * Math.PI.toFloat() / 180f
        val skew = cos(angle.toDouble()).toFloat() * size * 0.18f
        val lift = sin(angle.toDouble()).toFloat() * size * 0.12f
        val face = RectF(cx - size / 2f + skew, cy - size / 2f - lift,
            cx + size / 2f + skew, cy + size / 2f - lift)

        facePaint.color = Color.parseColor("#F1EEE7")
        canvas.drawRoundRect(face, size * 0.14f, size * 0.14f, facePaint)
        val side = Path()
        side.moveTo(face.right, face.top + size * 0.14f)
        side.lineTo(face.right + size * 0.22f, face.top + size * 0.03f)
        side.lineTo(face.right + size * 0.22f, face.bottom - size * 0.08f)
        side.lineTo(face.right, face.bottom)
        side.close()
        facePaint.color = Color.parseColor("#C8C4BC")
        canvas.drawPath(side, facePaint)
        if (modelAssetAvailable) {
            facePaint.color = Color.argb(24, 255, 255, 255)
            canvas.drawRoundRect(
                RectF(face.left + size * 0.08f, face.top + size * 0.08f,
                    face.right - size * 0.08f, face.bottom - size * 0.08f),
                size * 0.1f, size * 0.1f, facePaint
            )
        }
        canvas.drawRoundRect(face, size * 0.14f, size * 0.14f, edgePaint)
        drawPips(canvas, face, value)
    }

    private fun drawPips(canvas: Canvas, face: RectF, number: Int) {
        val unitX = face.width() * 0.25f
        val unitY = face.height() * 0.25f
        val points = when (number.coerceIn(1, 6)) {
            1 -> listOf(1 to 1)
            2 -> listOf(0 to 0, 2 to 2)
            3 -> listOf(0 to 0, 1 to 1, 2 to 2)
            4 -> listOf(0 to 0, 2 to 0, 0 to 2, 2 to 2)
            5 -> listOf(0 to 0, 2 to 0, 1 to 1, 0 to 2, 2 to 2)
            else -> listOf(0 to 0, 2 to 0, 0 to 1, 2 to 1, 0 to 2, 2 to 2)
        }
        val radius = face.width() * 0.075f
        for ((x, y) in points) {
            canvas.drawCircle(face.left + face.width() * 0.25f + unitX * x,
                face.top + face.height() * 0.25f + unitY * y, radius, pipPaint)
        }
    }

    fun rollTo(nextValue: Int, onFinished: () -> Unit) {
        animator?.cancel()
        isRolling = true
        val start = spin
        val end = start + 720f + (nextValue * 37f)
        animator = ValueAnimator.ofFloat(start, end).apply {
            duration = 720L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                spin = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    value = nextValue.coerceIn(1, 6)
                    isRolling = false
                    invalidate()
                    onFinished()
                }
            })
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && !isRolling) onRoll?.invoke()
        return true
    }
}