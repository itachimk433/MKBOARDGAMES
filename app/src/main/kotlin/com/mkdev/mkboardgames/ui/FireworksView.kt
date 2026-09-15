package com.mkdev.mkboardgames.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * A short, deterministic fireworks celebration rendered over the board.
 *
 * The staggered launches, glowing trails, gravity, and fading sparks keep this
 * lightweight enough for an Android canvas while making the celebration feel
 * more physical than a flat particle burst.
 */
class FireworksView(context: Context) : View(context) {
    private data class Spark(
        val angle: Float,
        val speed: Float,
        val size: Float,
        val delay: Float,
        val twinkle: Float,
    )

    private data class Burst(
        val launchX: Float,
        val targetX: Float,
        val targetY: Float,
        val launchAt: Float,
        val launchDuration: Float,
        val lifetime: Float,
        val color: Int,
        val sparks: List<Spark>,
    )

    private val animator = ValueAnimator.ofFloat(0f, 1f)
    private var progress = 0f
    private var onFinished: (() -> Unit)? = null
    private val bursts = createBursts()

    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        visibility = GONE
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        animator.duration = 3_650L
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener {
            progress = it.animatedFraction
            invalidate()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                progress = 1f
                visibility = GONE
                onFinished?.invoke()
            }
        })
    }

    fun playOnce(onFinished: (() -> Unit)? = null) {
        animator.cancel()
        this.onFinished = onFinished
        progress = 0f
        visibility = VISIBLE
        animator.start()
    }

    fun cancel() {
        animator.cancel()
        progress = 0f
        visibility = GONE
        onFinished = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0 || visibility != VISIBLE) return

        val size = min(width, height).toFloat()
        bursts.forEach { burst ->
            drawLaunch(canvas, burst, size)
            drawBurst(canvas, burst, size)
        }
    }

    private fun drawLaunch(canvas: Canvas, burst: Burst, size: Float) {
        val launchProgress = ((progress - burst.launchAt) / burst.launchDuration)
            .coerceIn(0f, 1f)
        if (progress < burst.launchAt || launchProgress >= 1f) return

        val startX = width * burst.launchX
        val startY = height * 1.05f
        val targetX = width * burst.targetX
        val targetY = height * burst.targetY
        val eased = launchProgress * launchProgress
        val x = startX + (targetX - startX) * eased
        val y = startY + (targetY - startY) * eased
        val tailProgress = (launchProgress - 0.16f).coerceAtLeast(0f) / 0.84f
        val tailX = startX + (targetX - startX) * (eased * tailProgress)
        val tailY = startY + (targetY - startY) * (eased * tailProgress)

        trailPaint.color = Color.argb(210, 255, 246, 204)
        trailPaint.strokeWidth = size * 0.008f
        trailPaint.setShadowLayer(size * 0.035f, 0f, 0f, Color.WHITE)
        canvas.drawLine(tailX, tailY, x, y, trailPaint)
        trailPaint.clearShadowLayer()

        glowPaint.color = Color.WHITE
        glowPaint.setShadowLayer(size * 0.06f, 0f, 0f, burst.color)
        canvas.drawCircle(x, y, size * 0.014f, glowPaint)
        glowPaint.clearShadowLayer()
    }

    private fun drawBurst(canvas: Canvas, burst: Burst, size: Float) {
        val explosionProgress = progress - burst.launchAt - burst.launchDuration
        if (explosionProgress < 0f) return

        val life = (explosionProgress / burst.lifetime).coerceIn(0f, 1f)
        val centerX = width * burst.targetX
        val centerY = height * burst.targetY
        if (life < 0.06f) {
            val flashAlpha = ((0.06f - life) / 0.06f * 230f).toInt()
            flashPaint.alpha = flashAlpha
            canvas.drawCircle(centerX, centerY, size * (0.035f + life * 0.23f), flashPaint)
            flashPaint.alpha = 255
        }

        burst.sparks.forEach { spark ->
            val sparkLife = ((life - spark.delay) / (1f - spark.delay)).coerceIn(0f, 1f)
            if (sparkLife <= 0f) return@forEach

            val distance = size * spark.speed * sparkLife * (1f - 0.13f * sparkLife)
            val gravity = size * 0.22f * sparkLife * sparkLife
            val x = centerX + cos(spark.angle.toDouble()).toFloat() * distance
            val y = centerY + sin(spark.angle.toDouble()).toFloat() * distance + gravity
            val previousLife = (sparkLife - 0.075f).coerceAtLeast(0f)
            val previousDistance = size * spark.speed * previousLife *
                (1f - 0.13f * previousLife)
            val previousGravity = size * 0.22f * previousLife * previousLife
            val previousX = centerX +
                cos(spark.angle.toDouble()).toFloat() * previousDistance
            val previousY = centerY +
                sin(spark.angle.toDouble()).toFloat() * previousDistance + previousGravity
            val fade = (1f - sparkLife).coerceIn(0f, 1f)
            val twinkle = 0.72f + 0.28f *
                sin((sparkLife * 22f + spark.twinkle).toDouble()).toFloat()
            val alpha = (fade * twinkle * 255f).toInt().coerceIn(0, 255)

            trailPaint.color = withAlpha(burst.color, alpha)
            trailPaint.strokeWidth = size * spark.size * (0.45f + fade * 0.55f)
            trailPaint.setShadowLayer(size * 0.022f, 0f, 0f, burst.color)
            canvas.drawLine(previousX, previousY, x, y, trailPaint)
            trailPaint.clearShadowLayer()

            if (sparkLife < 0.82f && spark.size > 0.006f) {
                glowPaint.color = withAlpha(Color.WHITE, (alpha * 0.72f).toInt())
                glowPaint.setShadowLayer(size * 0.025f, 0f, 0f, burst.color)
                canvas.drawCircle(x, y, size * spark.size * 0.58f, glowPaint)
                glowPaint.clearShadowLayer()
            }
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun createBursts(): List<Burst> {
        val random = Random(100100)
        fun burst(
            launchX: Float,
            targetX: Float,
            targetY: Float,
            launchAt: Float,
            color: Int,
            count: Int,
        ): Burst {
            val sparks = List(count) {
                Spark(
                    angle = random.nextFloat() * (Math.PI.toFloat() * 2f),
                    speed = 0.16f + random.nextFloat() * 0.21f,
                    size = 0.0045f + random.nextFloat() * 0.0065f,
                    delay = random.nextFloat() * 0.09f,
                    twinkle = random.nextFloat() * 7f,
                )
            }
            return Burst(
                launchX = launchX,
                targetX = targetX,
                targetY = targetY,
                launchAt = launchAt,
                launchDuration = 0.13f,
                lifetime = 0.67f + random.nextFloat() * 0.16f,
                color = color,
                sparks = sparks,
            )
        }

        return listOf(
            burst(0.18f, 0.27f, 0.31f, 0.04f, Color.rgb(255, 211, 72), 62),
            burst(0.74f, 0.69f, 0.25f, 0.18f, Color.rgb(109, 194, 255), 70),
            burst(0.47f, 0.48f, 0.18f, 0.38f, Color.rgb(255, 107, 145), 78),
            burst(0.88f, 0.81f, 0.43f, 0.56f, Color.rgb(182, 132, 255), 56),
            burst(0.08f, 0.16f, 0.52f, 0.71f, Color.rgb(96, 235, 171), 60),
        )
    }
}