package com.mkdev.mkboardgames.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
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
            if (value && frameAssetName == null) startGlowAnimation() else stopGlowAnimation()
            invalidate()
        }

    var frameAssetName: String? = null
        set(value) {
            if (field == value) return
            field = value
            themedFrameBitmap = value?.let(::loadFrameBitmap)
            if (value != null) stopGlowAnimation() else if (isActive) startGlowAnimation()
            invalidate()
        }

    var dieScale: Float = 1f
        set(value) {
            field = value.coerceIn(0.5f, 1f)
            requestLayout()
            invalidate()
        }

    /**
     * Moves only the die horizontally by a fraction of the device width.
     * The profile badge and control frame remain anchored in their corner.
     */
    var dieHorizontalShiftFraction: Float = 0f
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    private var glowAnimator: ValueAnimator? = null
    private var glowPulse = 0f
    private var themedFrameBitmap: Bitmap? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val themedFramePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
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
        val dieSize = dp(renderedDieSizeDp())
        val overlap = dp(2)
        val dieShift = (resources.displayMetrics.widthPixels * dieHorizontalShiftFraction)
            .roundToInt()
        val profileLeft = if (profileOnEnd) {
            dp(PAIR_WIDTH) - avatarWidth
        } else {
            0
        }
        val dieLeft = if (profileOnEnd) {
            ((profileLeft - dieSize) / 2 + dieShift).coerceAtLeast(0)
        } else {
            avatarWidth - overlap + dieShift
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
            LayoutParams(dieSize, dieSize).apply {
                leftMargin = dieLeft
                topMargin = frameTop + (frameHeight - dieSize) / 2
            },
        )
    }

    fun setDieVisible(visible: Boolean) {
        val die = getChildAt(1) ?: return
        die.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        (die as? GlbDiceView)?.setGameplayVisible(visible)
    }

    fun profileCenterInParent(): PointF {
        val profile = getChildAt(0) ?: return PointF()
        val params = profile.layoutParams as? MarginLayoutParams
        return PointF(
            (params?.leftMargin ?: profile.left).toFloat() + profile.measuredWidth / 2f,
            (params?.topMargin ?: profile.top).toFloat() + profile.measuredHeight / 2f,
        )
    }

    fun profileRadius(): Float =
        (getChildAt(0) as? LudoPlayerBadgeView)?.avatarRadius()
            ?: minOf(measuredWidth, measuredHeight) * 0.25f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(dp(PAIR_WIDTH), widthMeasureSpec)
        val height = resolveSize(dp(CONTROL_HEIGHT), heightMeasureSpec)
        setMeasuredDimension(width, height)
        for (index in 0 until childCount) {
            val isProfile = index == 0
            val childWidth = if (isProfile) AVATAR_SIZE else renderedDieSizeDp()
            val childHeight = if (isProfile) FRAME_HEIGHT else renderedDieSizeDp()
            val childWidthSpec = MeasureSpec.makeMeasureSpec(dp(childWidth), MeasureSpec.EXACTLY)
            val childHeightSpec = MeasureSpec.makeMeasureSpec(dp(childHeight), MeasureSpec.EXACTLY)
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

        val usesThemedFrame = frameAssetName != null && themedFrameBitmap != null
        if (usesThemedFrame) {
            fillPaint.clearShadowLayer()
            canvas.drawBitmap(themedFrameBitmap!!, null, frameRect, themedFramePaint)
        } else {
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
                dp(2f * GLOW_THICKNESS_SCALE * BORDER_THICKNESS_SCALE)
            } else {
                dp(1f * BORDER_THICKNESS_SCALE)
            }
            canvas.drawRoundRect(frameRect, radius, radius, borderPaint)
        }
    }

    private fun drawLabel(canvas: Canvas) {
        textPaint.color = if (isActive) Color.WHITE else Color.rgb(224, 232, 240)
        textPaint.textSize = dp(16).toFloat()
        textPaint.clearShadowLayer()
        if (frameAssetName != null) {
            textPaint.setShadowLayer(
                dp(7f),
                0f,
                0f,
                Color.argb(
                    220,
                    Color.red(accentColor),
                    Color.green(accentColor),
                    Color.blue(accentColor),
                ),
            )
        }
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

    private fun renderedDieSizeDp(): Int =
        ((if (frameAssetName != null) DIE_SIZE / 1.2f else DIE_SIZE.toFloat()) * dieScale)
            .roundToInt()

    private fun loadFrameBitmap(assetName: String): Bitmap? = runCatching {
        context.assets.open(assetName).use {
            BitmapFactory.decodeStream(it)
        }
    }.getOrNull()

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
        if (isActive && frameAssetName == null) startGlowAnimation()
    }

    override fun onDetachedFromWindow() {
        stopGlowAnimation()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val GLOW_THICKNESS_SCALE = 1.2f
        private const val BORDER_THICKNESS_SCALE = 0.81f
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