package com.mkdev.mkboardgames.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.mkdev.mkboardgames.SoundPlayer
import kotlin.math.min

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
    private val fullScreen = isFullScreenStyledGameLabel(gameLabel)
    private val isChess = gameLabel.replace(" ", "").equals("CHESS", ignoreCase = true)
    private val contentHeightDp = when {
        isChess && hasResumeMatch -> 548f
        isChess -> 500f
        hasResumeMatch -> 462f
        else -> 414f
    }
    private val chessHomeIconBitmap = try {
        context.assets.open("chess_home_icon.png").use { BitmapFactory.decodeStream(it) }
    } catch (_: Throwable) {
        null
    }
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
    private val chessBackdropPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chessHeroPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private lateinit var actions: List<MenuAction>
    private var pressedAction: MenuAction? = null
    private var resumePressed = false
    private var actionScale = HashMap<String, Float>()
    private var actionAnimator: ValueAnimator? = null
    private val resumeRect = RectF()
    private var downX = 0f
    private var downY = 0f
    private var contentOffset = 0f

    init {
        isClickable = true
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = contentHeightDp * unit
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = if (fullScreen && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else {
            resolveSize(desiredHeight.toInt(), heightMeasureSpec)
        }
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        val contentHeight = contentHeightDp * unit
        contentOffset = if (fullScreen) {
            ((height - contentHeight) / 2f).coerceAtLeast(0f)
        } else {
            0f
        }
        val sidePadding = 18f * unit
        val gap = 10f * unit
        val actionWidth = (width - sidePadding * 2f - gap) / 2f
        val actionTop = contentOffset + (if (isChess) 244f else 164f) * unit
        val actionHeight = 82f * unit

        actions = listOf(
            MenuAction(
                label = "vs AI",
                detail = "Challenge the board",
                symbol = if (isChess) {
                    "♞"
                } else {
                    ""
                },
                accent = Color.parseColor("#E3B86A"),
                action = { onVsAi?.invoke() },
            ),
            MenuAction(
                label = "2 Players",
                detail = "Play on one board",
                symbol = if (isChess) "♙" else "",
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
            contentOffset + contentHeight - 56f * unit,
            width * 0.82f,
            contentOffset + contentHeight - 8f * unit,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        if (isChess) {
            drawChessBackdrop(canvas, width, height)
            drawChessHero(canvas, width, contentOffset)
            drawChessHeader(canvas, width, contentOffset)
        } else {
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
            if (fullScreen) {
                canvas.drawRect(0f, 0f, width, height, surfacePaint)
            } else {
                canvas.drawRoundRect(0f, 0f, width, height, corner, corner, surfacePaint)
            }
            surfacePaint.shader = null
        }

        if (!isChess) {
            drawHeader(canvas, width, contentOffset)
        }
        actions.forEach { drawAction(canvas, it) }
        if (hasResumeMatch) {
            drawResumeAction(canvas, width)
        } else {
            canvas.drawText(
                "Choose your move. The board is waiting.",
                width / 2f,
                contentOffset + contentHeightDp * unit - 17f * unit,
                footerPaint,
            )
        }
    }

    private fun drawChessBackdrop(canvas: Canvas, width: Float, height: Float) {
        chessBackdropPaint.shader = LinearGradient(
            0f,
            0f,
            width * 0.9f,
            height,
            intArrayOf(
                Color.parseColor("#112C68"),
                Color.parseColor("#173C78"),
                Color.parseColor("#102951"),
                Color.parseColor("#061321"),
            ),
            floatArrayOf(0f, 0.32f, 0.68f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width, height, chessBackdropPaint)
        chessBackdropPaint.shader = null

        drawChessGlow(canvas, width * 0.12f, height * 0.18f, min(width, height) * 0.58f, Color.rgb(53, 137, 220))
        drawChessGlow(canvas, width * 0.9f, height * 0.64f, min(width, height) * 0.5f, Color.rgb(22, 194, 190))
        drawChessGlow(canvas, width * 0.46f, height * 1.02f, min(width, height) * 0.68f, Color.rgb(71, 37, 134))

        val horizon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                height * 0.48f,
                width,
                height * 0.58f,
                intArrayOf(
                    Color.argb(0, 104, 194, 255),
                    Color.argb(54, 77, 158, 232),
                    Color.argb(0, 104, 194, 255),
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, height * 0.4f, width, height * 0.65f, horizon)

        val stars = Paint(Paint.ANTI_ALIAS_FLAG)
        for (index in 0 until 62) {
            val x = ((index * 83 + 37) % 1000) / 1000f * width
            val y = ((index * 47 + 23) % 920) / 1000f * height
            val radius = (0.55f + (index % 4) * 0.45f) * unit
            stars.color = Color.argb(70 + (index % 5) * 28, 220, 241, 255)
            canvas.drawCircle(x, y, radius, stars)
            if (index % 11 == 0) {
                stars.color = Color.argb(130, 178, 224, 255)
                canvas.drawCircle(x, y, radius * 2.4f, stars)
            }
        }
        stars.color = Color.argb(34, 88, 207, 220)
        canvas.drawCircle(width * 0.08f, height * 0.72f, min(width, height) * 0.18f, stars)
        stars.color = Color.argb(25, 150, 109, 226)
        canvas.drawCircle(width * 0.88f, height * 0.3f, min(width, height) * 0.2f, stars)
    }

    private fun drawChessGlow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        chessBackdropPaint.shader = RadialGradient(
            x,
            y,
            radius,
            intArrayOf(
                Color.argb(88, Color.red(color), Color.green(color), Color.blue(color)),
                Color.argb(24, Color.red(color), Color.green(color), Color.blue(color)),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, radius, chessBackdropPaint)
        chessBackdropPaint.shader = null
    }

    private fun drawChessHero(canvas: Canvas, width: Float, topOffset: Float) {
        chessHomeIconBitmap?.let { bitmap ->
            val size = min(width * 0.36f, 150f * unit)
            val top = topOffset + 18f * unit
            chessHeroPaint.alpha = 255
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(
                    (width - size) / 2f,
                    top,
                    (width + size) / 2f,
                    top + size,
                ),
                chessHeroPaint,
            )
        }
    }

    private fun drawChessHeader(canvas: Canvas, width: Float, topOffset: Float) {
        titlePaint.color = Color.WHITE
        titlePaint.textSize = 24f * textScale
        canvas.drawText("Choose your match", width / 2f, topOffset + 196f * unit, titlePaint)
        subtitlePaint.color = Color.parseColor("#D6E8FF")
        canvas.drawText(
            "A good game starts with the right opponent.",
            width / 2f,
            topOffset + 217f * unit,
            subtitlePaint,
        )
    }

    private fun drawHeader(canvas: Canvas, width: Float, topOffset: Float) {
        val center = width / 2f
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D7A94D")
            strokeWidth = 1.5f * unit
        }
        canvas.drawLine(
            center - 118f * unit,
            topOffset + 36f * unit,
            center - 42f * unit,
            topOffset + 36f * unit,
            linePaint,
        )
        canvas.drawLine(
            center + 42f * unit,
            topOffset + 36f * unit,
            center + 118f * unit,
            topOffset + 36f * unit,
            linePaint,
        )
        canvas.drawText(
            "●",
            center,
            topOffset + 43f * unit,
            actionSymbolPaint.apply {
            color = Color.parseColor("#E3B86A")
            textSize = 21f * textScale
        })
        canvas.drawText(gameLabel, center, topOffset + 58f * unit, eyebrowPaint)
        canvas.drawText("Choose your match", center, topOffset + 99f * unit, titlePaint)
        canvas.drawText(
            "A good game starts with the right opponent.",
            center,
            topOffset + 125f * unit,
            subtitlePaint,
        )
    }

    private fun drawAction(canvas: Canvas, action: MenuAction) {
        if (isChess) {
            drawChessAction(canvas, action)
            return
        }
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

        if (action.symbol.isNotBlank()) {
            actionSymbolPaint.color = action.accent
            actionSymbolPaint.textSize = 20f * textScale
            canvas.drawText(action.symbol, rect.centerX(), rect.top + 25f * unit, actionSymbolPaint)
            actionLabelPaint.color = Color.WHITE
            canvas.drawText(action.label, rect.centerX(), rect.top + 52f * unit, actionLabelPaint)
            canvas.drawText(action.detail, rect.centerX(), rect.top + 69f * unit, actionDetailPaint)
        } else {
            drawCenteredActionText(canvas, rect, action)
        }
        canvas.restore()
    }

    private fun drawChessAction(canvas: Canvas, action: MenuAction) {
        val rect = action.rect
        val pressed = pressedAction == action
        drawChessWoodButton(canvas, rect, pressed, unit)
        val drawnTop = rect.top + if (pressed) 2f * unit else 0f
        actionSymbolPaint.color = Color.parseColor("#63301F")
        actionSymbolPaint.textSize = 23f * textScale
        canvas.drawText(action.symbol, rect.centerX(), drawnTop + 28f * unit, actionSymbolPaint)
        actionLabelPaint.color = Color.parseColor("#4A1714")
        canvas.drawText(action.label, rect.centerX(), drawnTop + 55f * unit, actionLabelPaint)
        actionDetailPaint.color = Color.parseColor("#6A2D1B")
        canvas.drawText(action.detail, rect.centerX(), drawnTop + 72f * unit, actionDetailPaint)
    }

    private fun drawCenteredActionText(canvas: Canvas, rect: RectF, action: MenuAction) {
        actionLabelPaint.color = Color.WHITE
        val labelMetrics = actionLabelPaint.fontMetrics
        val detailMetrics = actionDetailPaint.fontMetrics
        val labelHeight = labelMetrics.descent - labelMetrics.ascent
        val detailHeight = detailMetrics.descent - detailMetrics.ascent
        val gap = 3f * unit
        val groupHeight = labelHeight + gap + detailHeight
        val groupTop = rect.centerY() - groupHeight / 2f
        val labelBaseline = groupTop - labelMetrics.ascent
        val detailBaseline = groupTop + labelHeight + gap - detailMetrics.ascent
        canvas.drawText(action.label, rect.centerX(), labelBaseline, actionLabelPaint)
        canvas.drawText(action.detail, rect.centerX(), detailBaseline, actionDetailPaint)
    }

    private fun drawResumeAction(canvas: Canvas, width: Float) {
        if (isChess) {
            drawChessResumeAction(canvas, width)
            return
        }
        panelPaint.color = if (resumePressed) Color.parseColor("#21454A") else Color.parseColor("#16353B")
        canvas.drawRoundRect(resumeRect, 8f * unit, 8f * unit, panelPaint)
        panelBorderPaint.color = Color.parseColor("#2C5960")
        canvas.drawRoundRect(resumeRect, 8f * unit, 8f * unit, panelBorderPaint)
        canvas.drawText("RESUME SAVED MATCH", width / 2f, resumeRect.top + 20f * unit, resumePaint)
        canvas.drawText("Tap here to continue your last game", width / 2f, resumeRect.top + 38f * unit, footerPaint)
    }

    private fun drawChessResumeAction(canvas: Canvas, width: Float) {
        drawChessWoodButton(canvas, resumeRect, resumePressed, unit)
        val drawnTop = resumeRect.top + if (resumePressed) 2f * unit else 0f
        resumePaint.color = Color.parseColor("#4A1714")
        canvas.drawText("RESUME SAVED MATCH", width / 2f, drawnTop + 20f * unit, resumePaint)
        footerPaint.color = Color.parseColor("#6A2D1B")
        canvas.drawText("Tap here to continue your last game", width / 2f, drawnTop + 38f * unit, footerPaint)
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
                pressedAction?.let { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.hypot((event.x - downX).toDouble(), (event.y - downY).toDouble()) > 18f * unit) {
                    if (!isChess) pressedAction?.let { animateAction(it, 1f) }
                    pressedAction = null
                    resumePressed = false
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val action = pressedAction
                if (!isChess) action?.let { animateAction(it, 1f) }
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
                if (!isChess) pressedAction?.let { animateAction(it, 1f) }
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