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
) : View(context) {

    var onVsAi: (() -> Unit)? = null
    var onTwoPlayers: (() -> Unit)? = null
    var onHowToPlay: (() -> Unit)? = null
    var onPlayAs: (() -> Unit)? = null
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

    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * unit
    }
    private val boardLightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E5C99A")
    }
    private val boardDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#916447")
    }
    private val boardFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D9A85B")
        style = Paint.Style.STROKE
        strokeWidth = 2f * unit
    }
    private val boardHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 245, 194, 96)
    }
    private val piecePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(3f * unit, 0f, 2f * unit, Color.argb(130, 0, 0, 0))
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
        isFakeBoldText = true
        textSize = 14f * textScale
    }
    private val actionDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9FB5B8")
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

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        val sidePadding = 18f * unit
        val gap = 10f * unit
        val actionWidth = (width - sidePadding * 2f - gap) / 2f
        val actionTop = minOf(height * 0.53f, 300f * unit)
        val actionHeight = 78f * unit

        actions = listOf(
            MenuAction(
                label = "vs AI",
                detail = "Challenge the board",
                symbol = "♞",
                accent = Color.parseColor("#E3B86A"),
                action = { onVsAi?.invoke() },
            ),
            MenuAction(
                label = "2 Players",
                detail = "Play on one board",
                symbol = "♙",
                accent = Color.parseColor("#8EC7B9"),
                action = { onTwoPlayers?.invoke() },
            ),
            MenuAction(
                label = "How To Player",
                detail = "Learn the essentials",
                symbol = "?",
                accent = Color.parseColor("#A9B6E8"),
                action = { onHowToPlay?.invoke() },
            ),
            MenuAction(
                label = "Play As",
                detail = "Choose your colour",
                symbol = "↔",
                accent = Color.parseColor("#D9958F"),
                action = { onPlayAs?.invoke() },
            ),
        )

        actions.forEachIndexed { index, action ->
            val column = index % 2
            val row = index / 2
            val left = sidePadding + column * (actionWidth + gap)
            val top = actionTop + row * (actionHeight + gap)
            action.rect = RectF(left, top, left + actionWidth, top + actionHeight)
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
        val corner = 26f * unit

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

        drawHeader(canvas, width, height)
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

    private fun drawHeader(canvas: Canvas, width: Float, height: Float) {
        canvas.drawText("CHESS", width / 2f, 32f * unit, eyebrowPaint)
        canvas.drawText("Choose your match", width / 2f, 62f * unit, titlePaint)
        canvas.drawText("A good game starts with the right opponent.", width / 2f, 84f * unit, subtitlePaint)

        val boardSize = minOf(width * 0.43f, 132f * unit)
        val left = width / 2f - boardSize / 2f
        val top = 102f * unit
        val cell = boardSize / 8f

        panelPaint.color = Color.argb(95, 0, 0, 0)
        canvas.drawRoundRect(
            left - 8f * unit,
            top - 8f * unit,
            left + boardSize + 8f * unit,
            top + boardSize + 8f * unit,
            12f * unit,
            12f * unit,
            panelPaint,
        )
        for (row in 0 until 8) {
            for (column in 0 until 8) {
                val square = if ((row + column) % 2 == 0) boardLightPaint else boardDarkPaint
                canvas.drawRect(
                    left + column * cell,
                    top + row * cell,
                    left + (column + 1) * cell,
                    top + (row + 1) * cell,
                    square,
                )
            }
        }
        canvas.drawRect(left, top, left + boardSize, top + boardSize, boardFramePaint)
        canvas.drawRect(
            left + cell * 4f,
            top + cell * 4f,
            left + cell * 5f,
            top + cell * 5f,
            boardHighlightPaint,
        )

        val position = arrayOf(
            "♜·♝·♚·♞♜",
            "♟♟♟·♟♟♟♟",
            "··♞·····",
            "·····♟··",
            "····♙···",
            "··♘··♘··",
            "♙♙♙·♕♙♙♙",
            "♖·♗·♔♗♘♖",
        )
        piecePaint.textSize = cell * 0.77f
        for (row in position.indices) {
            for (column in position[row].indices) {
                val piece = position[row][column]
                if (piece == '·') continue
                piecePaint.color = if (row < 4) Color.parseColor("#233039") else Color.parseColor("#FFF4D8")
                val metrics = piecePaint.fontMetrics
                val baseline = top + row * cell + cell / 2f -
                    (metrics.ascent + metrics.descent) / 2f
                canvas.drawText(
                    piece.toString(),
                    left + column * cell + cell / 2f,
                    baseline,
                    piecePaint,
                )
            }
        }
    }

    private fun drawAction(canvas: Canvas, action: MenuAction) {
        val scale = actionScale[action.label] ?: 1f
        val rect = action.rect
        val pressed = pressedAction == action

        canvas.save()
        canvas.scale(scale, scale, rect.centerX(), rect.centerY())

        panelPaint.color = if (pressed) Color.parseColor("#21454A") else Color.parseColor("#16353B")
        canvas.drawRoundRect(rect, 15f * unit, 15f * unit, panelPaint)
        panelBorderPaint.color = if (pressed) action.accent else Color.parseColor("#2C5960")
        canvas.drawRoundRect(
            RectF(rect.left + 0.5f * unit, rect.top + 0.5f * unit, rect.right - 0.5f * unit, rect.bottom - 0.5f * unit),
            15f * unit,
            15f * unit,
            panelBorderPaint,
        )

        panelPaint.color = action.accent
        canvas.drawRoundRect(
            rect.left,
            rect.top,
            rect.left + 4f * unit,
            rect.bottom,
            15f * unit,
            15f * unit,
            panelPaint,
        )
        actionSymbolPaint.color = action.accent
        canvas.drawText(action.symbol, rect.left + 29f * unit, rect.top + 34f * unit, actionSymbolPaint)
        actionLabelPaint.color = Color.WHITE
        canvas.drawText(action.label, rect.left + 54f * unit, rect.top + 31f * unit, actionLabelPaint)
        canvas.drawText(action.detail, rect.left + 54f * unit, rect.top + 51f * unit, actionDetailPaint)
        canvas.restore()
    }

    private fun drawResumeAction(canvas: Canvas, width: Float) {
        panelPaint.color = if (resumePressed) Color.parseColor("#21454A") else Color.parseColor("#16353B")
        canvas.drawRoundRect(resumeRect, 14f * unit, 14f * unit, panelPaint)
        panelBorderPaint.color = Color.parseColor("#2C5960")
        canvas.drawRoundRect(resumeRect, 14f * unit, 14f * unit, panelBorderPaint)
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