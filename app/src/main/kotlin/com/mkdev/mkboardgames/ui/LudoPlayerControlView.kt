package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
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

    var isActive: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

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
        fillPaint.clearShadowLayer()
        if (isActive) {
            fillPaint.setShadowLayer(
                dp(10).toFloat(),
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
        canvas.drawRoundRect(frameRect, radius, radius, fillPaint)

        borderPaint.color = if (isActive) {
            Color.WHITE
        } else {
            Color.argb(
                210,
                Color.red(accentColor),
                Color.green(accentColor),
                Color.blue(accentColor),
            )
        }
        borderPaint.strokeWidth = if (isActive) dp(2).toFloat() else dp(1).toFloat()
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
        canvas.drawText(label, width / 2f, baseline, textPaint)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    companion object {
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