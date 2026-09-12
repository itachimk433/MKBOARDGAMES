package com.mkdev.mkboardgames.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.roundToInt

/**
 * One corner's profile + die control.
 *
 * The player name intentionally lives outside the rounded frame. This keeps
 * the die and avatar inside one clean border while leaving the name readable
 * above the top controls and below the bottom controls.
 */
class LudoPlayerControlView(context: Context) : FrameLayout(context) {
    var label: String = ""
        set(value) {
            field = value
            contentDescription = "$value player controls"
            invalidate()
        }

    var accentColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    var labelBelow: Boolean = false
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var labelUpsideDown: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var isActive: Boolean = false
        set(value) {
            field = value
            if (value) startGlowAnimation() else stopGlowAnimation()
            invalidate()
        }

    private var glowAnimator: ValueAnimator? = null
    private var glowPulse = 0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    init {
        setWillNotDraw(false)
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        minimumHeight = dp(CONTROL_HEIGHT)
    }

    fun bind(profile: View, die: View, profileOnEnd: Boolean) {
        removeAllViews()
        val frameTop = if (labelBelow) 0 else dp(LABEL_HEIGHT)
        val frameHeight = dp(FRAME_HEIGHT)
        val avatarWidth = dp(AVATAR_SIZE)
        val dieSize = dp(DIE_SIZE)
        val overlap = dp(2)
        val profileLeft = if (profileOnEnd) {
            dp(PAIR_WIDTH) - avatarWidth
        } else {
            0
        }
        val dieLeft = if (profileOnEnd) {
            0
        } else {
            avatarWidth - overlap
        }

        addView(
            profile,
            LayoutParams(avatarWidth, frameHeight).apply {
                leftMargin = profileLeft
                topMargin = frameTop
            },
        )
        addView(
            die,
            LayoutParams(dieSize, frameHeight).apply {
                leftMargin = dieLeft
                topMargin = frameTop
            },
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(dp(PAIR_WIDTH), widthMeasureSpec)
        val height = resolveSize(dp(CONTROL_HEIGHT), heightMeasureSpec)
        setMeasuredDimension(width, height)
        val childHeightSpec = MeasureSpec.makeMeasureSpec(dp(FRAME_HEIGHT), MeasureSpec.EXACTLY)
        for (index in 0 until childCount) {
            val childWidth = if (index == 0) AVATAR_SIZE else DIE_SIZE
            val childWidthSpec = MeasureSpec.makeMeasureSpec(dp(childWidth), MeasureSpec.EXACTLY)
            getChildAt(index).measure(childWidthSpec, childHeightSpec)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        drawLabel(canvas)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val frameTop = if (labelBelow) 0 else dp(LABEL_HEIGHT)
        val frameRect = RectF(
            dp(1).toFloat(),
            (frameTop + dp(1)).toFloat(),
            (width - dp(1)).toFloat(),
            (frameTop + dp(FRAME_HEIGHT) - dp(1)).toFloat(),
        )
        val radius = dp(13).toFloat()

        fillPaint.color = Color.argb(238, 10, 18, 27)
        if (isActive) {
            val glowAlpha = (110 + (125 * glowPulse)).roundToInt()
            fillPaint.setShadowLayer(
                dp((7f + (13f * glowPulse)) * GLOW_THICKNESS_SCALE),
                0f,
                0f,
                Color.argb(
                    glowAlpha,
                    Color.red(accentColor),
                    Color.green(accentColor),
                    Color.blue(accentColor),
                ),
            )
        } else {
            fillPaint.clearShadowLayer()
        }
        canvas.drawRoundRect(frameRect, radius, radius, fillPaint)

        borderPaint.color = if (isActive) {
            Color.argb(
                (190 + (65 * glowPulse)).roundToInt(),
                Color.red(accentColor),
                Color.green(accentColor),
                Color.blue(accentColor),
            )
        } else {
            Color.argb(
                210,
                Color.red(accentColor),
                Color.green(accentColor),
                Color.blue(accentColor),
            )
        }
        borderPaint.strokeWidth = if (isActive) {
            dp(2f * GLOW_THICKNESS_SCALE)
        } else {
            dp(1).toFloat()
        }
        canvas.drawRoundRect(frameRect, radius, radius, borderPaint)
    }

    private fun drawLabel(canvas: Canvas) {
        textPaint.color = if (isActive) Color.WHITE else Color.rgb(224, 232, 240)
        textPaint.textSize = dp(16).toFloat()
        val baseline = if (labelBelow) {
            dp(FRAME_HEIGHT + 15).toFloat()
        } else {
            dp(14).toFloat()
        }
        val labelCenterY = baseline - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.save()
        if (labelUpsideDown) {
            canvas.rotate(180f, width / 2f, labelCenterY)
        }
        canvas.drawText(label, width / 2f, baseline, textPaint)
        canvas.restore()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density

    private fun startGlowAnimation() {
        if (!isAttachedToWindow || glowAnimator?.isRunning == true) return
        glowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                glowPulse = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopGlowAnimation() {
        glowAnimator?.cancel()
        glowAnimator = null
        glowPulse = 0f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isActive) startGlowAnimation()
    }

    override fun onDetachedFromWindow() {
        stopGlowAnimation()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val GLOW_THICKNESS_SCALE = 1.2f
        private const val LABEL_HEIGHT = 29
        private const val FRAME_HEIGHT = 74
        private const val AVATAR_SIZE = 70
        private const val DIE_SIZE = 74
        const val CONTROL_HEIGHT = LABEL_HEIGHT + FRAME_HEIGHT
        const val PAIR_WIDTH = AVATAR_SIZE + DIE_SIZE - 2
        const val RAIL_HEIGHT = 112
        const val CONTROL_GAP = 8
    }
}