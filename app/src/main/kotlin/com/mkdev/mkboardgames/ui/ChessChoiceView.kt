package com.mkdev.mkboardgames.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.mkdev.mkboardgames.SoundPlayer

/**
 * The shared Chess-styled choice surface used after selecting "vs AI".
 * Keeping it as a view instead of an AlertDialog makes the side picker feel
 * like part of the same game flow as the main Chess menu.
 */
class ChessChoiceView(
    context: Context,
    private val title: String,
    private val subtitle: String,
    choices: List<Choice>,
    private val gameLabel: String = "C H E S S",
    private val headerSymbol: String = if (
        gameLabel.replace(" ", "").contains("DRAUGHTS", ignoreCase = true)
    ) "◎" else "♛",
) : View(context) {

    data class Choice(
        val label: String,
        val detail: String,
        val symbol: String,
        val accent: Int,
    )

    var onChoiceSelected: ((Int) -> Unit)? = null

    private data class ChoiceHit(
        val choice: Choice,
        val index: Int,
        var rect: RectF = RectF(),
    )

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val unit = density.coerceAtLeast(1f)
    private val textScale = scaledDensity.coerceAtMost(2f)
    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * unit
    }
    private val crownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E3B86A")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 21f * textScale
    }
    private val eyebrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E3B86A")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        letterSpacing = 0.18f
        textSize = 11f * textScale
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 26f * textScale
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9FB5B8")
        textAlign = Paint.Align.CENTER
        textSize = 12f * textScale
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 23f * textScale
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 16f * textScale
    }
    private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9FB5B8")
        textAlign = Paint.Align.CENTER
        textSize = 11f * textScale
    }
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#71898D")
        textAlign = Paint.Align.CENTER
        textSize = 10f * textScale
    }

    private val hits = choices.mapIndexed { index, choice -> ChoiceHit(choice, index) }
    private val scales = HashMap<Int, Float>()
    private var pressedIndex: Int? = null
    private var animator: ValueAnimator? = null
    private var downX = 0f
    private var downY = 0f

    init {
        isClickable = true
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        hits.forEach { scales[it.index] = 1f }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (178f + hits.size * 104f) * unit
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = resolveSize(desiredHeight.toInt(), heightMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        val sidePadding = 22f * unit
        val top = 153f * unit
        val cardHeight = 88f * unit
        val gap = 12f * unit
        hits.forEachIndexed { index, hit ->
            val cardTop = top + index * (cardHeight + gap)
            hit.rect = RectF(sidePadding, cardTop, width - sidePadding, cardTop + cardHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        surfacePaint.shader = LinearGradient(
            0f,
            0f,
            width,
            height,
            Color.parseColor("#102C32"),
            Color.parseColor("#0B1D25"),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(0f, 0f, width, height, 12f * unit, 12f * unit, surfacePaint)
        surfacePaint.shader = null

        drawHeader(canvas, width)
        hits.forEach { drawChoice(canvas, it) }
    }

    private fun drawHeader(canvas: Canvas, width: Float) {
        val center = width / 2f
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D7A94D")
            strokeWidth = 1.5f * unit
        }
        canvas.drawLine(center - 118f * unit, 36f * unit, center - 42f * unit, 36f * unit, linePaint)
        canvas.drawLine(center + 42f * unit, 36f * unit, center + 118f * unit, 36f * unit, linePaint)
        canvas.drawText(headerSymbol, center, 43f * unit, crownPaint)
        canvas.drawText(gameLabel, center, 58f * unit, eyebrowPaint)
        canvas.drawText(title, center, 99f * unit, titlePaint)
        canvas.drawText(subtitle, center, 125f * unit, subtitlePaint)
    }

    private fun drawChoice(canvas: Canvas, hit: ChoiceHit) {
        val scale = scales[hit.index] ?: 1f
        val rect = hit.rect
        val pressed = pressedIndex == hit.index

        canvas.save()
        canvas.scale(scale, scale, rect.centerX(), rect.centerY())
        cardPaint.color = if (pressed) Color.parseColor("#21454A") else Color.parseColor("#16353B")
        canvas.drawRoundRect(rect, 8f * unit, 8f * unit, cardPaint)
        borderPaint.color = hit.choice.accent
        canvas.drawRoundRect(
            RectF(rect.left + 0.5f * unit, rect.top + 0.5f * unit, rect.right - 0.5f * unit, rect.bottom - 0.5f * unit),
            8f * unit,
            8f * unit,
            borderPaint,
        )
        iconPaint.color = hit.choice.accent
        canvas.drawText(hit.choice.symbol, rect.centerX(), rect.top + 29f * unit, iconPaint)
        canvas.drawText(hit.choice.label, rect.centerX(), rect.top + 56f * unit, labelPaint)
        canvas.drawText(hit.choice.detail, rect.centerX(), rect.top + 74f * unit, detailPaint)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                pressedIndex = hits.firstOrNull { it.rect.contains(event.x, event.y) }?.index
                pressedIndex?.let {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    animateScale(it, 0.95f)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.hypot((event.x - downX).toDouble(), (event.y - downY).toDouble()) > 18f * unit) {
                    pressedIndex?.let { animateScale(it, 1f) }
                    pressedIndex = null
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val selected = pressedIndex
                selected?.let { animateScale(it, 1f) }
                if (selected != null && hits[selected].rect.contains(event.x, event.y)) {
                    SoundPlayer.play("ui_click")
                    onChoiceSelected?.invoke(selected)
                }
                pressedIndex = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedIndex?.let { animateScale(it, 1f) }
                pressedIndex = null
                invalidate()
                return true
            }
        }
        return true
    }

    private fun animateScale(index: Int, target: Float) {
        animator?.cancel()
        val current = scales[index] ?: 1f
        animator = ValueAnimator.ofFloat(current, target).apply {
            duration = if (target < 1f) 80L else 130L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                scales[index] = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
}