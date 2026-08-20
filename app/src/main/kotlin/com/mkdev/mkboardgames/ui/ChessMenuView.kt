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
 * A focused setup surface for Chess.
 *
 * The regular game catalogue is intentionally left alone. Chess gets a
 * dedicated board-like surface because it is the most frequently opened game
 * and benefits from making the next action obvious at a glance.
 */
class ChessMenuView(
    context: Context,
    private val hasResumeMatch: Boolean,
    private val gameLabel: String = "C H E S S",
) : View(context) {

    var onVsAi: (() -> Unit)? = null
    var onTwoPlayers: (() -> Unit)? = null
    var onHowToPlay: (() -> Unit)? = null
    var onResumeMatch: (() -> Unit)? = null

    private data class MenuAction(
        val label: String,
        val detail: String,
        val symbol: String,
        val accent: Int,
        val action: () -> Unit,
        var rect: RectF = RectF(),
    )

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val unit = density.coerceAtLeast(1f)
    private val textScale = scaledDensity.coerceAtMost(2f)
    private val isConnectFour = gameLabel.replace(" ", "").contains("CONNECT", ignoreCase = true)
    private val isFoxAndGeese = gameLabel.replace(" ", "").contains("FOX", ignoreCase = true)

    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * unit
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
        textSize = 27f * textScale
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9FB5B8")
        textAlign = Paint.Align.CENTER
        textSize = 12f * textScale
    }
    private val actionLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 14f * textScale
    }
    private val actionDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9FB5B8")
        textAlign = Paint.Align.CENTER
        textSize = 10f * textScale
    }
    private val actionSymbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 23f * textScale
    }
    private val resumePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E3B86A")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 11f * textScale
    }
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#71898D")
        textAlign = Paint.Align.CENTER
        textSize = 10f * textScale
    }

    private lateinit var actions: List<MenuAction>
    private var pressedAction: MenuAction? = null
    private var resumePressed = false
    private var actionScale = HashMap<String, Float>()
    private var actionAnimator: ValueAnimator? = null
    private val resumeRect = RectF()
    private var downX = 0f
    private var downY = 0f

    init {
        isClickable = true
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = if (hasResumeMatch) 462f * unit else 414f * unit
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = resolveSize(desiredHeight.toInt(), heightMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        val sidePadding = 18f * unit
        val gap = 10f * unit
        val actionWidth = (width - sidePadding * 2f - gap) / 2f
        val actionTop = 164f * unit
        val actionHeight = 82f * unit

        actions = listOf(
            MenuAction(
                label = "vs AI",
                detail = "Challenge the board",
                symbol = when {
                    isConnectFour -> "●"
                    isFoxAndGeese -> "🦊"
                    else -> "♞"
                },
                accent = Color.parseColor("#E3B86A"),
                action = { onVsAi?.invoke() },
            ),
            MenuAction(
                label = "2 Players",
                detail = "Play on one board",
                symbol = when {
                    isConnectFour -> "●"
                    isFoxAndGeese -> "🪿"
                    else -> "♙"
                },
                accent = Color.parseColor("#8EC7B9"),
                action = { onTwoPlayers?.invoke() },
            ),
            MenuAction(
                label = "How To Play",
                detail = "Learn the essentials",
                symbol = "?",
                accent = Color.parseColor("#A9B6E8"),
                action = { onHowToPlay?.invoke() },
            ),
        )

        actions.forEachIndexed { index, action ->
            if (index < 2) {
                val left = sidePadding + index * (actionWidth + gap)
                action.rect = RectF(left, actionTop, left + actionWidth, actionTop + actionHeight)
            } else {
                val top = actionTop + actionHeight + gap
                action.rect = RectF(sidePadding, top, width - sidePadding, top + actionHeight)
            }
            actionScale[action.label] = 1f
        }
        resumeRect.set(
            width * 0.18f,
            height - 56f * unit,
            width * 0.82f,
            height - 8f * unit,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        val corner = 12f * unit

        surfacePaint.shader = LinearGradient(
            0f,
            0f,
            width,
            height,
            Color.parseColor("#102C32"),
            Color.parseColor("#0B1D25"),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(0f, 0f, width, height, corner, corner, surfacePaint)
        surfacePaint.shader = null

        drawHeader(canvas, width)
        actions.forEach { drawAction(canvas, it) }
        if (hasResumeMatch) {
            drawResumeAction(canvas, width)
        } else {
            canvas.drawText(
                "Choose your move. The board is waiting.",
                width / 2f,
                height - 17f * unit,
                footerPaint,
            )
        }
    }

    private fun drawHeader(canvas: Canvas, width: Float) {
        val center = width / 2f
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D7A94D")
            strokeWidth = 1.5f * unit
        }
        canvas.drawLine(center - 118f * unit, 36f * unit, center - 42f * unit, 36f * unit, linePaint)
        canvas.drawLine(center + 42f * unit, 36f * unit, center + 118f * unit, 36f * unit, linePaint)
        canvas.drawText(
            when {
                isConnectFour -> "●"
                isFoxAndGeese -> "🦊"
                else -> "♛"
            },
            center,
            43f * unit,
            actionSymbolPaint.apply {
            color = Color.parseColor("#E3B86A")
            textSize = 21f * textScale
        })
        canvas.drawText(gameLabel, center, 58f * unit, eyebrowPaint)
        canvas.drawText("Choose your match", center, 99f * unit, titlePaint)
        canvas.drawText("A good game starts with the right opponent.", center, 125f * unit, subtitlePaint)
    }

    private fun drawAction(canvas: Canvas, action: MenuAction) {
        val scale = actionScale[action.label] ?: 1f
        val rect = action.rect
        val pressed = pressedAction == action

        canvas.save()
        canvas.scale(scale, scale, rect.centerX(), rect.centerY())

        panelPaint.color = if (pressed) Color.parseColor("#21454A") else Color.parseColor("#16353B")
        canvas.drawRoundRect(rect, 8f * unit, 8f * unit, panelPaint)
        panelBorderPaint.color = if (pressed) action.accent else Color.parseColor("#2C5960")
        canvas.drawRoundRect(
            RectF(rect.left + 0.5f * unit, rect.top + 0.5f * unit, rect.right - 0.5f * unit, rect.bottom - 0.5f * unit),
            8f * unit,
            8f * unit,
            panelBorderPaint,
        )

        actionSymbolPaint.color = action.accent
        actionSymbolPaint.textSize = 20f * textScale
        canvas.drawText(action.symbol, rect.centerX(), rect.top + 25f * unit, actionSymbolPaint)
        actionLabelPaint.color = Color.WHITE
        canvas.drawText(action.label, rect.centerX(), rect.top + 52f * unit, actionLabelPaint)
        canvas.drawText(action.detail, rect.centerX(), rect.top + 69f * unit, actionDetailPaint)
        canvas.restore()
    }

    private fun drawResumeAction(canvas: Canvas, width: Float) {
        panelPaint.color = if (resumePressed) Color.parseColor("#21454A") else Color.parseColor("#16353B")
        canvas.drawRoundRect(resumeRect, 8f * unit, 8f * unit, panelPaint)
        panelBorderPaint.color = Color.parseColor("#2C5960")
        canvas.drawRoundRect(resumeRect, 8f * unit, 8f * unit, panelBorderPaint)
        canvas.drawText("RESUME SAVED MATCH", width / 2f, resumeRect.top + 20f * unit, resumePaint)
        canvas.drawText("Tap here to continue your last game", width / 2f, resumeRect.top + 38f * unit, footerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                pressedAction = actions.firstOrNull { it.rect.contains(event.x, event.y) }
                if (pressedAction == null && hasResumeMatch && resumeRect.contains(event.x, event.y)) {
                    resumePressed = true
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                pressedAction?.let {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    animateAction(it, 0.95f)
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.hypot((event.x - downX).toDouble(), (event.y - downY).toDouble()) > 18f * unit) {
                    pressedAction?.let { animateAction(it, 1f) }
                    pressedAction = null
                    resumePressed = false
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val action = pressedAction
                action?.let { animateAction(it, 1f) }
                if (action != null && action.rect.contains(event.x, event.y)) {
                    SoundPlayer.play("ui_click")
                    action.action()
                } else if (hasResumeMatch && resumePressed && resumeRect.contains(event.x, event.y)) {
                    SoundPlayer.play("ui_click")
                    onResumeMatch?.invoke()
                }
                pressedAction = null
                resumePressed = false
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedAction?.let { animateAction(it, 1f) }
                pressedAction = null
                resumePressed = false
                invalidate()
                return true
            }
        }
        return true
    }

    private fun animateAction(action: MenuAction, target: Float) {
        actionAnimator?.cancel()
        val current = actionScale[action.label] ?: 1f
        actionAnimator = ValueAnimator.ofFloat(current, target).apply {
            duration = if (target < 1f) 80L else 130L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                actionScale[action.label] = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
}